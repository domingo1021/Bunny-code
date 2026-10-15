package com.bunnycode.project.application;

import com.bunnycode.identity.UserSummary;
import com.bunnycode.project.domain.Visibility;
import java.time.Instant;
import java.util.List;

public record ProjectDetailView(long id, String name, String description, Visibility visibility,
                                long watchCount, long starCount, Instant createdAt, UserSummary owner,
                                boolean starredByViewer, List<VersionView> versions) {

    public ProjectDetailView {
        versions = List.copyOf(versions);
    }
}
