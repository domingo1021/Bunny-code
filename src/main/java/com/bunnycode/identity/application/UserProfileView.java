package com.bunnycode.identity.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A user's profile. {@code email} is only present when users look at their own profile. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileView(long id, String name, String email, String pictureUrl, String profile, int level) {
}
