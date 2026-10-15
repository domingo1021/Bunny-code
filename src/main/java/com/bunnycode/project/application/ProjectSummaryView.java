package com.bunnycode.project.application;

import com.bunnycode.identity.UserSummary;
import com.bunnycode.project.domain.Visibility;
import java.time.Instant;

public record ProjectSummaryView(long id, String name, String description, Visibility visibility,
                                 long watchCount, long starCount, Instant createdAt, String latestVersionName,
                                 UserSummary owner) {
}
