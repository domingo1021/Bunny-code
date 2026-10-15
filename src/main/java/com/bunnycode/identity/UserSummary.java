package com.bunnycode.identity;

import java.util.Objects;

/** Public display data of a user. */
public record UserSummary(long id, String name, String pictureUrl) {

    public UserSummary {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pictureUrl, "pictureUrl");
    }
}
