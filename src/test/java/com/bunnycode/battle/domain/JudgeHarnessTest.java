package com.bunnycode.battle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("JudgeHarness")
class JudgeHarnessTest {

    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("captures stdout/JSON before the user's code runs, then calls the entry function once per case")
    void programLayout() {
        String program = JudgeHarness.program("function twoSum(a, t) { return [0, 1]; }", "twoSum",
                List.of("[[2,7],9]", "[[1],2]"), NONCE);

        int prelude = program.indexOf("process.stdout.write.bind");
        int userCode = program.indexOf("function twoSum");
        int runner = program.indexOf("twoSum(...args)");
        assertThat(prelude).isNotNegative().isLessThan(userCode);
        assertThat(userCode).isLessThan(runner);
        assertThat(program).contains("[[2,7],9]", "[[1],2]").contains("@@bunny-judge:" + NONCE + "@@");
    }

    @Test
    @DisplayName("a trailing line comment in user code cannot swallow the runner")
    void trailingComment() {
        String program = JudgeHarness.program("function f() { return 1; } // done", "f", List.of("[]"), NONCE);
        assertThat(program).contains("// done\n;(() => {");
    }

    @ParameterizedTest
    @ValueSource(strings = {"f()", "a b", "1abc", "x;process.exit()", ""})
    @DisplayName("refuses entry-function names that are not plain identifiers")
    void rejectsBadIdentifiers(String name) {
        assertThatThrownBy(() -> JudgeHarness.program("", name, List.of(), NONCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("finds the last report line and separates it from what the user printed")
    void parsesOutput() {
        String stdout = "debug 1\n@@bunny-judge:" + NONCE + "@@[{\"ok\":true,\"value\":1}]\n";

        assertThat(JudgeHarness.report(stdout, NONCE)).contains("[{\"ok\":true,\"value\":1}]");
        assertThat(JudgeHarness.userOutput(stdout, NONCE)).isEqualTo("debug 1");
    }

    @Test
    @DisplayName("a report forged with another nonce is ignored")
    void ignoresForgedReports() {
        String forged = "@@bunny-judge:ffffffffffffffffffffffffffffffff@@[{\"ok\":true}]";
        assertThat(JudgeHarness.report(forged, NONCE)).isEmpty();
    }
}
