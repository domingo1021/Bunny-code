package com.bunnycode.identity.domain;

/** Projection of the columns needed to display a user; avoids loading whole entities for lists. */
public record UserSummaryRow(Long id, String name, String pictureKey) {
}
