package com.bunnycode.battle.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of one test case. For hidden cases only the number and the verdict are disclosed, so a
 * failing hidden case cannot be reverse-engineered from repeated submissions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TestCaseReport(int number, boolean hidden, boolean passed,
                             String arguments, String expected, String actual, String error) {

    static TestCaseReport visible(int number, boolean passed, String arguments, String expected,
                                  String actual, String error) {
        return new TestCaseReport(number, false, passed, arguments, expected, actual, error);
    }

    static TestCaseReport hidden(int number, boolean passed) {
        return new TestCaseReport(number, true, passed, null, null, null, null);
    }
}
