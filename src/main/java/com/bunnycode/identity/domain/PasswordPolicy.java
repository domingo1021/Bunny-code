package com.bunnycode.identity.domain;

/** Password rule for new accounts (unchanged from v1 so the client can keep its form hints). */
public final class PasswordPolicy {

    /** 6–30 characters with at least one lower-case letter and one digit. */
    public static final String REGEX = "^(?=.*[a-z])(?=.*\\d).{6,30}$";

    public static final String MESSAGE =
            "Passwords need 6–30 characters with at least one lower-case letter and one digit.";

    private PasswordPolicy() {
    }
}
