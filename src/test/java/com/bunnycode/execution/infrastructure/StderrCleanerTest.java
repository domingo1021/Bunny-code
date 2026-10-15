package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StderrCleaner")
class StderrCleanerTest {

    @Test
    @DisplayName("keeps the code frame and message, drops stack frames and the Node banner")
    void cleans() {
        String stderr = """
                [stdin]:2
                undefinedFunction();
                ^

                ReferenceError: undefinedFunction is not defined
                    at [stdin]:2:1
                    at runScriptInThisContext (node:internal/vm:209:10)

                Node.js v24.21.0
                """;

        assertThat(StderrCleaner.clean(stderr)).isEqualTo("""
                [stdin]:2
                undefinedFunction();
                ^

                ReferenceError: undefinedFunction is not defined""");
    }
}
