package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.bdd.ApiClient.ApiResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

public class BattleSteps {

    private static final String INVITATIONS = "/topic/battle-invitations";
    private static final String MY_BATTLES = "/user/queue/battles";
    private static final String ERRORS = "/user/queue/errors";
    private static final String WRONG_HELLO = "function helloWorld(s) { return 'nope'; }";

    private final World world;
    private final ApiClient api;
    private final SimpUserRegistry userRegistry;
    private final int port;
    /** Everyone subscribed to a battle room: players and spectators. */
    private final Map<String, Set<String>> rooms = new HashMap<>();
    private final Map<String, String> submittedCode = new HashMap<>();

    public BattleSteps(World world, ApiClient api, SimpUserRegistry userRegistry, @Value("${local.server.port}") int port) {
        this.world = world;
        this.api = api;
        this.userRegistry = userRegistry;
        this.port = port;
    }

    @Given("{string}, {string} and {string} are online")
    public void online(String first, String second, String third) {
        for (String user : List.of(first, second, third)) {
            StompTestSession session = StompTestSession.connect(port, world.tokenOf(user));
            session.subscribe(INVITATIONS);
            session.subscribe(MY_BATTLES);
            session.subscribe(ERRORS);
            world.connected(user, session);
            awaitSubscribed(user, INVITATIONS);
        }
    }

    @When("{string} invites everyone to a {string} battle called {string}")
    public void invite(String inviter, String level, String battleName) {
        world.session(inviter).send("/app/battle-invitations", Map.of("battleName", battleName, "level", level));
    }

    @Then("{string} and {string} see an invitation to {string} from {string}")
    public void seeInvitation(String first, String second, String battleName, String inviter) {
        for (String user : List.of(first, second)) {
            JsonNode invitation = invitationFor(user, battleName);
            assertThat(invitation.path("inviterName").asString()).isEqualTo(inviter);
            assertThat(invitation.path("inviterId").asLong()).isEqualTo(world.account(inviter).id());
        }
    }

    @When("{string} accepts the invitation to {string}")
    public void accept(String user, String battleName) {
        String invitationId = invitationFor(user, battleName).path("invitationId").asString();
        world.session(user).send("/app/battle-invitations/" + invitationId + "/accept", null);
    }

    @Then("{string} and {string} are sent to the battle {string}")
    public void sentToBattle(String first, String second, String battleName) {
        long battleId = world.session(first).await(MY_BATTLES, message -> message.has("battleId")).path("battleId").asLong();
        assertThat(world.session(second).await(MY_BATTLES, message -> message.has("battleId")).path("battleId").asLong())
                .isEqualTo(battleId);
        world.rememberBattle(battleName, battleId);
    }

    @Then("{string} is told {string}")
    public void isTold(String user, String code) {
        world.session(user).await(ERRORS, message -> message.path("code").asString().equals(code));
    }

    @Given("{string} and {string} are in a {string} battle called {string}")
    public void inBattle(String inviter, String accepter, String level, String battleName) {
        invite(inviter, level, battleName);
        accept(accepter, battleName);
        sentToBattle(inviter, accepter, battleName);
        enter(inviter, battleName, "PLAYER");
        enter(accepter, battleName, "PLAYER");
    }

    @Given("{string} is watching {string}")
    public void watching(String spectator, String battleName) {
        enter(spectator, battleName, "SPECTATOR");
    }

    @When("{string} and {string} get ready in {string}")
    public void getReady(String first, String second, String battleName) {
        long battleId = world.battleId(battleName);
        world.session(first).send("/app/battles/" + battleId + "/ready", null);
        world.session(second).send("/app/battles/" + battleId + "/ready", null);
        world.session(first).await(room(battleName), event -> type(event, "STARTED"));
    }

    @Then("everyone in {string} sees it start exactly once")
    public void startsOnce(String battleName) {
        for (String participant : rooms.get(battleName)) {
            world.session(participant).await(room(battleName), event -> type(event, "STARTED"));
        }
        Awaitility.await().pollInSameThread().pollDelay(Duration.ofMillis(500)).until(() -> true);
        for (String participant : rooms.get(battleName)) {
            List<JsonNode> events = world.session(participant).messages(room(battleName));
            assertThat(events.stream().filter(event -> type(event, "STARTED")).count()).as(participant).isEqualTo(1);
            assertThat(events.stream().filter(event -> type(event, "PLAYER_READY")).count()).as(participant).isEqualTo(2);
        }
    }

    @When("{string} submits in {string}:")
    public void submit(String player, String battleName, String code) {
        submittedCode.put(battleName + "/" + player, code);
        world.session(player).send("/app/battles/" + world.battleId(battleName) + "/submissions", Map.of("code", code));
    }

    @Then("everyone in {string} sees {string} judged {string} with {int} submissions left")
    public void judged(String battleName, String player, String verdict, int chancesLeft) {
        long playerId = world.account(player).id();
        for (String participant : rooms.get(battleName)) {
            JsonNode judged = world.session(participant).await(room(battleName), event -> type(event, "SUBMISSION_JUDGED")
                    && event.path("userId").asLong() == playerId && event.path("verdict").asString().equals(verdict));
            assertThat(judged.path("chancesLeft").asInt()).isEqualTo(chancesLeft);
        }
    }

    @Then("the failing test case shown to {string} in {string} is visible with expected output {string}")
    public void visibleFailure(String participant, String battleName, String expected) {
        JsonNode failing = lastJudgement(participant, battleName).path("testCases").path(-1);
        JsonNode lastCase = lastCase(participant, battleName);
        assertThat(lastCase.path("hidden").asBoolean()).isFalse();
        assertThat(lastCase.path("passed").asBoolean()).isFalse();
        assertThat(lastCase.path("expected").asString()).isEqualTo(expected);
        assertThat(lastCase.path("actual").asString()).isNotBlank();
        assertThat(failing).isNotNull();
    }

    @Then("the failing test case shown to {string} in {string} is hidden")
    public void hiddenFailure(String participant, String battleName) {
        JsonNode lastCase = lastCase(participant, battleName);
        assertThat(lastCase.path("hidden").asBoolean()).isTrue();
        assertThat(lastCase.path("passed").asBoolean()).isFalse();
        assertThat(lastCase.has("expected")).isFalse();
        assertThat(lastCase.has("arguments")).isFalse();
        assertThat(lastCase.path("number").asInt()).isEqualTo(4);
    }

    @Then("everyone in {string} sees {string} win")
    public void winner(String battleName, String player) {
        for (String participant : rooms.get(battleName)) {
            JsonNode finished = world.session(participant).await(room(battleName), event -> type(event, "FINISHED"));
            assertThat(finished.path("winnerId").asLong()).isEqualTo(world.account(player).id());
            assertThat(finished.path("winnerName").asString()).isEqualTo(player);
        }
    }

    @Then("the battle {string} is {string} with {string} as the winner")
    public void finishedWithWinner(String battleName, String status, String player) {
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode detail = battleDetail(battleName);
            assertThat(detail.path("status").asString()).isEqualTo(status);
            assertThat(detail.path("winner").path("id").asLong()).isEqualTo(world.account(player).id());
        });
    }

    @Then("the winning code of {string} is archived")
    public void winningCodeArchived(String battleName) {
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String url = battleDetail(battleName).path("winnerCodeUrl").asString();
            assertThat(url).isNotBlank();
            String code = RestClient.create().get().uri(url).retrieve().body(String.class);
            assertThat(code).isEqualTo(submittedCode.get(battleName + "/" + winnerName(battleName)));
        });
    }

    @Then("{string} has been watched {int} time(s)")
    public void watchCount(String battleName, int times) {
        assertThat(battleDetail(battleName).path("watchCount").asInt()).isEqualTo(times);
    }

    @When("{string} types {string} in {string}")
    public void types(String player, String code, String battleName) {
        world.session(player).send("/app/battles/" + world.battleId(battleName) + "/code", Map.of("code", code));
    }

    @Then("{string} sees the code of {string} in {string} become {string}")
    public void seesCode(String spectator, String player, String battleName, String code) {
        long playerId = world.account(player).id();
        world.session(spectator).await(room(battleName), event -> type(event, "CODE_CHANGED")
                && event.path("userId").asLong() == playerId && event.path("code").asString().equals(code));
    }

    @When("{string} leaves {string}")
    public void leaves(String player, String battleName) {
        world.session(player).send("/app/battles/" + world.battleId(battleName) + "/leave", null);
    }

    @Then("everyone in {string} sees it terminated because {string}")
    public void terminated(String battleName, String reason) {
        for (String participant : rooms.get(battleName)) {
            world.session(participant).await(room(battleName),
                    event -> type(event, "TERMINATED") && event.path("reason").asString().equals(reason));
        }
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(battleDetail(battleName).path("status").asString()).isEqualTo("TERMINATED"));
    }

    @When("{string} submits a wrong answer {int} times in {string}")
    public void submitsWrongAnswers(String player, int times, String battleName) {
        long playerId = world.account(player).id();
        for (int attempt = 1; attempt <= times; attempt++) {
            int expectedLeft = 3 - attempt;
            world.session(player).send("/app/battles/" + world.battleId(battleName) + "/submissions",
                    Map.of("code", WRONG_HELLO));
            world.session(player).await(room(battleName), event -> type(event, "SUBMISSION_JUDGED")
                    && event.path("userId").asLong() == playerId && event.path("chancesLeft").asInt() == expectedLeft);
        }
    }

    @When("{string} tries to get ready in {string}")
    public void triesToGetReady(String spectator, String battleName) {
        world.session(spectator).send("/app/battles/" + world.battleId(battleName) + "/ready", null);
    }

    @Then("the lobby lists {string}")
    public void lobbyLists(String names) {
        assertThat(lobbyNames()).containsExactly(names.split(",\\s*"));
    }

    @Then("the lobby lists {string} in any order")
    public void lobbyListsInAnyOrder(String names) {
        assertThat(lobbyNames()).containsExactlyInAnyOrder(names.split(",\\s*"));
    }

    private List<String> lobbyNames() {
        return ProjectSteps.stream(world.lastResponse().body().path("content"))
                .map(battle -> battle.path("name").asString())
                .toList();
    }

    private void enter(String user, String battleName, String expectedRole) {
        long battleId = world.battleId(battleName);
        StompTestSession session = world.session(user);
        session.subscribe(room(battleName));
        String snapshotDestination = "/app/battles/" + battleId;
        session.subscribe(snapshotDestination);
        JsonNode snapshot = session.await(snapshotDestination, message -> message.has("viewerRole"));
        assertThat(snapshot.path("viewerRole").asString()).isEqualTo(expectedRole);
        assertThat(snapshot.path("question").path("name").asString()).isNotBlank();
        assertThat(snapshot.path("players").size()).isEqualTo(2);
        rooms.computeIfAbsent(battleName, key -> new LinkedHashSet<>()).add(user);
    }

    private JsonNode invitationFor(String user, String battleName) {
        return world.session(user).await(INVITATIONS, message -> message.path("battleName").asString().equals(battleName));
    }

    private JsonNode lastJudgement(String participant, String battleName) {
        List<JsonNode> judgements = new ArrayList<>(world.session(participant).messages(room(battleName)).stream()
                .filter(event -> type(event, "SUBMISSION_JUDGED")).toList());
        assertThat(judgements).isNotEmpty();
        return judgements.getLast();
    }

    private JsonNode lastCase(String participant, String battleName) {
        JsonNode cases = lastJudgement(participant, battleName).path("testCases");
        assertThat(cases.size()).isPositive();
        return cases.path(cases.size() - 1);
    }

    private JsonNode battleDetail(String battleName) {
        ApiResponse response = api.get("/api/v2/battles/" + world.battleId(battleName), null);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    private String winnerName(String battleName) {
        return battleDetail(battleName).path("winner").path("name").asString();
    }

    private String room(String battleName) {
        return "/topic/battles/" + world.battleId(battleName);
    }

    private void awaitSubscribed(String user, String destination) {
        String userId = String.valueOf(world.account(user).id());
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(() -> !userRegistry.findSubscriptions(subscription ->
                subscription.getDestination().equals(destination)
                        && subscription.getSession().getUser().getName().equals(userId)).isEmpty());
    }

    private static boolean type(JsonNode event, String type) {
        return type.equals(event.path("type").asString());
    }
}
