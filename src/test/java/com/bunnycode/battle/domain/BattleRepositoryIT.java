package com.bunnycode.battle.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.MySqlTestConfiguration;
import com.bunnycode.shared.web.LikePatterns;
import com.bunnycode.support.TestData;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // the race test needs real, separate transactions
@DisplayName("Battle repositories (MySQL)")
class BattleRepositoryIT {

    @Autowired
    private BattleRepository battles;
    @Autowired
    private QuestionRepository questions;
    @Autowired
    private JdbcTemplate jdbc;

    private TestData data;
    private long alice;
    private long bob;
    private long twoSum;

    @BeforeEach
    void seed() {
        data = new TestData(jdbc);
        data.clear();
        alice = data.user("alice");
        bob = data.user("bob");
        twoSum = data.questionId("Two sum");
    }

    @Test
    @DisplayName("V3 seeded each level with a question whose five test cases load in one fetch-join")
    void seededQuestions() {
        assertThat(questions.findIdsByLevel(QuestionLevel.EASY)).hasSize(1);
        assertThat(questions.findWithTestCases(twoSum)).get().satisfies(question -> {
            assertThat(question.getEntryFunction()).isEqualTo("twoSum");
            assertThat(question.getLevel()).isEqualTo(QuestionLevel.MEDIUM);
            assertThat(question.getTestCases()).extracting(TestCase::getNumber).containsExactly(1, 2, 3, 4, 5);
            assertThat(question.getTestCases().getFirst().getArgumentsJson()).isEqualTo("[[2,7,11,15],9]");
        });
    }

    @Test
    @DisplayName("transitions are compare-and-set: the second identical transition changes nothing")
    void compareAndSet() {
        long id = data.battle("duel", alice, bob, twoSum, "WAITING", 0);

        assertThat(battles.transition(id, BattleStatus.WAITING, BattleStatus.RUNNING)).isEqualTo(1);
        assertThat(battles.transition(id, BattleStatus.WAITING, BattleStatus.RUNNING)).isZero();
        assertThat(battles.findById(id)).get().extracting(Battle::getStatus).isEqualTo(BattleStatus.RUNNING);
    }

    @Test
    @DisplayName("when both players' winning submissions race, exactly one becomes the winner")
    void onlyOneWinner() throws Exception {
        long id = data.battle("race", alice, bob, twoSum, "RUNNING", 0);
        List<Callable<Integer>> attempts = List.of(
                () -> battles.finish(id, alice, BattleStatus.RUNNING, BattleStatus.FINISHED),
                () -> battles.finish(id, bob, BattleStatus.RUNNING, BattleStatus.FINISHED));

        int winners;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            winners = pool.invokeAll(attempts).stream().mapToInt(BattleRepositoryIT::get).sum();
        }

        assertThat(winners).isEqualTo(1);
        Long winnerId = jdbc.queryForObject("select winner_id from battle where battle_id = ?", Long.class, id);
        assertThat(winnerId).isIn(alice, bob);
    }

    @Test
    @DisplayName("lobby queries filter by phase and search by battle name, question name or player ids")
    void lobby() {
        data.battle("alpha", alice, bob, twoSum, "RUNNING", 5);
        data.battle("beta", alice, bob, data.questionId("Hello world"), "WAITING", 9);
        data.battle("gamma", bob, alice, twoSum, "FINISHED", 1);
        long carol = data.user("carol");
        long dave = data.user("dave");
        data.battle("delta", carol, dave, twoSum, "TERMINATED", 0);
        var active = EnumSet.of(BattleStatus.WAITING, BattleStatus.RUNNING);
        var page = PageRequest.of(0, 10);

        assertThat(battles.findByStatuses(active, null, page)).extracting(BattleSummaryRow::name).containsExactly("beta", "alpha");
        assertThat(battles.searchByName(active, null, LikePatterns.contains("lph"), page)).extracting(BattleSummaryRow::name)
                .containsExactly("alpha");
        assertThat(battles.searchByQuestion(active, null, LikePatterns.contains("hello"), page))
                .extracting(BattleSummaryRow::name).containsExactly("beta");
        assertThat(battles.findByPlayers(EnumSet.allOf(BattleStatus.class), null, Set.of(carol), page))
                .extracting(BattleSummaryRow::name).containsExactly("delta");

        // Level filter: "Two sum" is MEDIUM, "Hello world" is EASY.
        assertThat(battles.findByStatuses(active, QuestionLevel.EASY, page)).extracting(BattleSummaryRow::name)
                .containsExactly("beta");
        assertThat(battles.findByStatuses(EnumSet.allOf(BattleStatus.class), QuestionLevel.MEDIUM, page).getTotalElements())
                .isEqualTo(3);
        assertThat(battles.searchByName(active, QuestionLevel.HARD, LikePatterns.contains("a"), page)).isEmpty();
        assertThat(battles.countByStatuses(EnumSet.of(BattleStatus.WAITING, BattleStatus.RUNNING, BattleStatus.FINISHED)))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("watch count and winner code are updated in place")
    void updates() {
        long id = data.battle("watched", alice, bob, twoSum, "RUNNING", 0);

        IntStream.range(0, 3).forEach(i -> battles.incrementWatchCount(id));
        battles.attachWinnerCode(id, "/battle/battle_1/1.js");

        assertThat(battles.findWithQuestion(id)).get().satisfies(battle -> {
            assertThat(battle.getWatchCount()).isEqualTo(3);
            assertThat(battle.getWinnerCodeKey()).isEqualTo("/battle/battle_1/1.js");
            assertThat(battle.getQuestion().getName()).isEqualTo("Two sum");
        });
    }

    private static int get(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
