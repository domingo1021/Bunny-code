package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.JudgeHarness;
import com.bunnycode.battle.domain.Question;
import com.bunnycode.battle.domain.TestCase;
import com.bunnycode.battle.domain.Verdict;
import com.bunnycode.execution.CodeExecutor;
import com.bunnycode.execution.ExecutionResult;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a submission against a question's test cases and compares results as JSON values
 * (structural equality — no string munging of console output as in v1).
 */
@Component
public class Judge {

    private static final SecureRandom NONCES = new SecureRandom();

    private final CodeExecutor executor;
    private final JsonMapper json;
    private final BattleProperties properties;

    Judge(CodeExecutor executor, JsonMapper json, BattleProperties properties) {
        this.executor = executor;
        this.json = json;
        this.properties = properties;
    }

    public Judgement judge(Question question, String code) {
        List<TestCase> cases = question.getTestCases();
        String nonce = newNonce();
        String program = JudgeHarness.program(code, question.getEntryFunction(),
                cases.stream().map(TestCase::getArgumentsJson).toList(), nonce);
        return switch (executor.execute(program)) {
            case ExecutionResult.Succeeded(String stdout) -> evaluate(cases, stdout, nonce);
            case ExecutionResult.Failed(String stdout, String stderr) ->
                    new Judgement(Verdict.RUNTIME_ERROR, List.of(), stderr);
            case ExecutionResult.TimedOut(var limit) -> new Judgement(Verdict.TIME_LIMIT_EXCEEDED, List.of(),
                    "Your code ran for more than %d seconds and was stopped.".formatted(limit.toSeconds()));
            case ExecutionResult.OutOfMemory() -> new Judgement(Verdict.MEMORY_LIMIT_EXCEEDED, List.of(),
                    "Your code ran out of memory and was stopped.");
        };
    }

    private Judgement evaluate(List<TestCase> cases, String stdout, String nonce) {
        String printed = JudgeHarness.userOutput(stdout, nonce);
        Optional<String> report = JudgeHarness.report(stdout, nonce);
        if (report.isEmpty()) {
            return new Judgement(Verdict.RUNTIME_ERROR, List.of(),
                    "Your program stopped before the tests could run. Did it call process.exit()?");
        }
        JsonNode results = json.readTree(report.get());
        List<TestCaseReport> reports = new ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            TestCase testCase = cases.get(i);
            JsonNode result = results.path(i);
            boolean ok = result.path("ok").asBoolean(false);
            JsonNode actual = result.path("value");
            boolean passed = ok && json.readTree(testCase.getExpectedJson()).equals(actual);
            reports.add(reportOf(testCase, passed, ok ? json.writeValueAsString(actual) : null,
                    ok ? null : result.path("error").asString("Unknown error")));
            if (!passed) {
                return new Judgement(ok ? Verdict.WRONG_ANSWER : Verdict.RUNTIME_ERROR, reports, printed);
            }
        }
        return new Judgement(Verdict.ACCEPTED, reports, printed);
    }

    private TestCaseReport reportOf(TestCase testCase, boolean passed, String actual, String error) {
        if (testCase.getNumber() > properties.visibleTestCases()) {
            return TestCaseReport.hidden(testCase.getNumber(), passed);
        }
        return TestCaseReport.visible(testCase.getNumber(), passed, testCase.getArgumentsJson(),
                testCase.getExpectedJson(), actual, error);
    }

    private static String newNonce() {
        byte[] bytes = new byte[16];
        NONCES.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
