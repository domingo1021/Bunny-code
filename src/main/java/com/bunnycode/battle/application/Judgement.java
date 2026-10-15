package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.Verdict;
import java.util.List;
import java.util.Objects;

/**
 * @param testCases reports up to and including the first failing case (as v1 did)
 * @param output    what the program printed, or the error / limit message
 */
public record Judgement(Verdict verdict, List<TestCaseReport> testCases, String output) {

    public Judgement {
        Objects.requireNonNull(verdict, "verdict");
        testCases = List.copyOf(testCases);
        Objects.requireNonNull(output, "output");
    }

    public boolean accepted() {
        return verdict == Verdict.ACCEPTED;
    }
}
