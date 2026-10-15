package com.bunnycode.identity.domain;

import com.bunnycode.shared.error.BunnyException;
import java.util.Locale;
import java.util.regex.Pattern;

/** A syntactically valid, normalised (trimmed, lower-case) e-mail address. */
public record EmailAddress(String value) {

    public static final String REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private static final Pattern PATTERN = Pattern.compile(REGEX);
    private static final int MAX_LENGTH = 255;

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw BunnyException.invalid("user.email-invalid", "Please enter a valid e-mail address.");
        }
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw == null ? null : raw.strip().toLowerCase(Locale.ROOT));
    }
}
