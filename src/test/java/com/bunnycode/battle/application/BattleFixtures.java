package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.Battle;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.Question;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.battle.domain.TestCase;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds read-only entities for unit tests (in production they come from the database). */
final class BattleFixtures {

    private BattleFixtures() {
    }

    static Question twoSum(int visibleAndHidden) {
        Question question = instantiate(Question.class);
        ReflectionTestUtils.setField(question, "id", 5L);
        ReflectionTestUtils.setField(question, "name", "Two sum");
        ReflectionTestUtils.setField(question, "level", QuestionLevel.MEDIUM);
        ReflectionTestUtils.setField(question, "entryFunction", "twoSum");
        ReflectionTestUtils.setField(question, "descriptionKey", "/question/two_sum/question.md");
        ReflectionTestUtils.setField(question, "starterCodeKey", "/question/two_sum/base.js");
        List<TestCase> cases = new ArrayList<>();
        for (int number = 1; number <= visibleAndHidden; number++) {
            TestCase testCase = instantiate(TestCase.class);
            ReflectionTestUtils.setField(testCase, "id", (long) number);
            ReflectionTestUtils.setField(testCase, "number", number);
            ReflectionTestUtils.setField(testCase, "argumentsJson", "[[" + number + "]," + number + "]");
            ReflectionTestUtils.setField(testCase, "expectedJson", "[" + number + "]");
            cases.add(testCase);
        }
        ReflectionTestUtils.setField(question, "testCases", cases);
        return question;
    }

    static Battle battle(long id, BattleStatus status) {
        Battle battle = Battle.between("duel", 1L, 2L, twoSum(1), Instant.EPOCH);
        ReflectionTestUtils.setField(battle, "id", id);
        ReflectionTestUtils.setField(battle, "status", status);
        return battle;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
