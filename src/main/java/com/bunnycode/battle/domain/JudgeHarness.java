package com.bunnycode.battle.domain;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the program that judges a submission, and finds its report in the output.
 *
 * <p>Layout of the generated program:
 * <ol>
 *   <li>a prelude that captures {@code JSON.stringify} and {@code process.stdout.write} under a
 *       per-run random name — before user code runs, so user code cannot replace them to forge
 *       the report;</li>
 *   <li>the user's code, verbatim;</li>
 *   <li>a runner that calls the entry function once per test case, catching errors per case,
 *       and prints one marker-prefixed JSON line.</li>
 * </ol>
 * Expected outputs never enter the program; comparison happens on the server, so hidden test
 * cases stay hidden. v1 instead kept one hand-written harness file per question on the sandbox
 * image and parsed {@code console.log} output with string replacements.
 */
public final class JudgeHarness {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_$][\\w$]{0,49}$");
    private static final Pattern NONCE = Pattern.compile("^[a-f0-9]{16,64}$");

    private JudgeHarness() {
    }

    public static String program(String userCode, String entryFunction, List<String> argumentsJson, String nonce) {
        Objects.requireNonNull(userCode, "userCode");
        if (!IDENTIFIER.matcher(entryFunction).matches()) {
            throw new IllegalArgumentException("Invalid entry function name: " + entryFunction);
        }
        requireNonce(nonce);
        String guard = "__bunny_" + nonce;
        String cases = argumentsJson.stream().collect(joining(",\n    ", "[\n    ", "\n  ]"));
        return """
                const %1$s = Object.freeze({ stringify: JSON.stringify, write: process.stdout.write.bind(process.stdout) });
                %2$s
                ;(() => {
                  const cases = %3$s;
                  const results = [];
                  for (const args of cases) {
                    try {
                      const value = %4$s(...args);
                      results.push({ ok: true, value: value === undefined ? null : value });
                    } catch (error) {
                      results.push({ ok: false, error: String((error && error.message) || error) });
                    }
                  }
                  %1$s.write("\\n%5$s" + %1$s.stringify(results) + "\\n");
                })();
                """.formatted(guard, userCode, cases, entryFunction, marker(nonce));
    }

    /** The JSON report printed by {@link #program}, if the program got that far. */
    public static Optional<String> report(String stdout, String nonce) {
        requireNonce(nonce);
        String marker = marker(nonce);
        return stdout.lines()
                .filter(line -> line.startsWith(marker))
                .reduce((first, second) -> second)
                .map(line -> line.substring(marker.length()));
    }

    /** Everything the user's own code printed, without the report line. */
    public static String userOutput(String stdout, String nonce) {
        String marker = marker(nonce);
        return stdout.lines().filter(line -> !line.startsWith(marker)).collect(joining("\n")).strip();
    }

    private static String marker(String nonce) {
        return "@@bunny-judge:" + nonce + "@@";
    }

    private static void requireNonce(String nonce) {
        if (nonce == null || !NONCE.matcher(nonce).matches()) {
            throw new IllegalArgumentException("Invalid nonce");
        }
    }
}
