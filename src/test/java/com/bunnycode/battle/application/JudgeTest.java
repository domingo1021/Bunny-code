package com.bunnycode.battle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bunnycode.battle.domain.Verdict;
import com.bunnycode.execution.CodeExecutor;
import com.bunnycode.execution.ExecutionResult;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Judge")
class JudgeTest {

    private static final Pattern MARKER = Pattern.compile("@@bunny-judge:[a-f0-9]+@@");

    private final CodeExecutor executor = mock(CodeExecutor.class);
    private final Judge judge = new Judge(executor, JsonMapper.builder().build(),
            new BattleProperties(Duration.ofSeconds(10), 3, 3, Duration.ofHours(1), 4));

    /** Simulates the sandbox: echoes the report the harness would print, using the run's own marker. */
    private void givenResults(String userOutput, String resultsJson) {
        given(executor.execute(anyString())).willAnswer(invocation -> {
            Matcher marker = MARKER.matcher(invocation.<String>getArgument(0));
            marker.find();
            return new ExecutionResult.Succeeded(userOutput + "\n" + marker.group() + resultsJson + "\n");
        });
    }

    @Test
    @DisplayName("Given every case returns its expected JSON, then the verdict is ACCEPTED")
    void accepted() {
        givenResults("hello", "[{\"ok\":true,\"value\":[1]},{\"ok\":true,\"value\":[2]},{\"ok\":true,\"value\":[3]},"
                + "{\"ok\":true,\"value\":[4]},{\"ok\":true,\"value\":[5]}]");

        Judgement judgement = judge.judge(BattleFixtures.twoSum(5), "code");

        assertThat(judgement.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(judgement.testCases()).hasSize(5).allMatch(TestCaseReport::passed);
        assertThat(judgement.output()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Given a visible case fails, then it is WRONG_ANSWER and its details are shown")
    void wrongAnswerVisible() {
        givenResults("", "[{\"ok\":true,\"value\":[1]},{\"ok\":true,\"value\":[99]}]");

        Judgement judgement = judge.judge(BattleFixtures.twoSum(5), "code");

        assertThat(judgement.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(judgement.testCases()).hasSize(2).last().satisfies(report -> {
            assertThat(report.hidden()).isFalse();
            assertThat(report.passed()).isFalse();
            assertThat(report.expected()).isEqualTo("[2]");
            assertThat(report.actual()).isEqualTo("[99]");
        });
    }

    @Test
    @DisplayName("Given a hidden case fails, then only its number and verdict are disclosed")
    void wrongAnswerHidden() {
        givenResults("", "[{\"ok\":true,\"value\":[1]},{\"ok\":true,\"value\":[2]},{\"ok\":true,\"value\":[3]},"
                + "{\"ok\":true,\"value\":[0]}]");

        Judgement judgement = judge.judge(BattleFixtures.twoSum(5), "code");

        assertThat(judgement.testCases().getLast()).isEqualTo(new TestCaseReport(4, true, false, null, null, null, null));
    }

    @Test
    @DisplayName("Given the function throws for a case, then it is RUNTIME_ERROR with the message")
    void throwsInCase() {
        givenResults("", "[{\"ok\":false,\"error\":\"twoSum is not defined\"}]");

        Judgement judgement = judge.judge(BattleFixtures.twoSum(5), "code");

        assertThat(judgement.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(judgement.testCases().getFirst().error()).isEqualTo("twoSum is not defined");
    }

    @Test
    @DisplayName("Given the program never reaches the runner (e.g. process.exit), then it is RUNTIME_ERROR")
    void noReport() {
        given(executor.execute(anyString())).willReturn(new ExecutionResult.Succeeded("bye\n"));
        assertThat(judge.judge(BattleFixtures.twoSum(5), "code").verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    @DisplayName("Sandbox limits map to TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED, and syntax errors to RUNTIME_ERROR")
    void limits() {
        given(executor.execute(anyString())).willReturn(new ExecutionResult.TimedOut(Duration.ofSeconds(10)));
        assertThat(judge.judge(BattleFixtures.twoSum(1), "code").verdict()).isEqualTo(Verdict.TIME_LIMIT_EXCEEDED);

        given(executor.execute(anyString())).willReturn(new ExecutionResult.OutOfMemory());
        assertThat(judge.judge(BattleFixtures.twoSum(1), "code").verdict()).isEqualTo(Verdict.MEMORY_LIMIT_EXCEEDED);

        given(executor.execute(anyString())).willReturn(new ExecutionResult.Failed("", "SyntaxError: Unexpected token"));
        Judgement failed = judge.judge(BattleFixtures.twoSum(1), "code");
        assertThat(failed.verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
        assertThat(failed.output()).isEqualTo("SyntaxError: Unexpected token");
    }
}
