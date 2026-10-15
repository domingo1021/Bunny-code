package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

public class WorkspaceSteps {

    private static final String EDIT_LOCK_QUEUE = "/user/queue/edit-lock";

    private final World world;
    private final ApiClient api;
    private final StringRedisTemplate redis;
    private final int port;
    private Throwable connectionFailure;

    public WorkspaceSteps(World world, ApiClient api, StringRedisTemplate redis, @Value("${local.server.port}") int port) {
        this.world = world;
        this.api = api;
        this.redis = redis;
        this.port = port;
    }

    @Given("{string} is connected in window {string}")
    public void connectWindow(String user, String window) {
        StompTestSession session = StompTestSession.connect(port, world.tokenOf(user));
        session.subscribe(EDIT_LOCK_QUEUE);
        session.subscribe("/user/queue/errors");
        world.connected(window, session);
    }

    @When("window {string} asks to edit version {int} of {string}")
    public void askToEdit(String window, int version, String project) {
        requestLock(window, version, project, false);
    }

    @When("window {string} takes over version {int} of {string}")
    public void takeOver(String window, int version, String project) {
        requestLock(window, version, project, true);
    }

    @Then("window {string} is told {string}")
    public void isTold(String window, String state) {
        var status = world.session(window).await(EDIT_LOCK_QUEUE, message -> message.path("state").asString().equals(state));
        assertThat(status.path("message").asString()).isNotBlank();
    }

    @When("window {string} is closed")
    public void closeWindow(String window) {
        int sessionsBefore = lockSessions().size();
        world.disconnect(window);
        Awaitility.await().pollInSameThread().atMost(Duration.ofSeconds(10)).until(() -> lockSessions().size() < sessionsBefore);
    }

    @When("someone connects to the realtime API with an invalid token")
    public void connectWithInvalidToken() {
        connectionFailure = catchThrowable(() -> StompTestSession.connect(port, "not-a-valid-token"));
    }

    @Then("the connection is refused")
    public void connectionRefused() {
        assertThat(connectionFailure).isNotNull();
    }

    private void requestLock(String window, int version, String project, boolean takeOver) {
        long versionId = ProjectSteps.versionId(api, project, version, null);
        world.session(window).send("/app/versions/" + versionId + "/edit-lock", Map.of("takeOver", takeOver));
    }

    private Set<String> lockSessions() {
        Set<String> keys = redis.keys("edit-lock:session:*");
        return keys == null ? Set.of() : keys;
    }
}
