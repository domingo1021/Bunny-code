package com.bunnycode.project.application;

import com.bunnycode.project.ProjectAccess;
import com.bunnycode.project.domain.ProjectVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One projection query answers ownership and "already recorded" together. This is the check
 * v1 got wrong: it tested a {@code COUNT(*)} row for truthiness, so every user passed.
 */
@Service
@Transactional(readOnly = true)
class ProjectAccessService implements ProjectAccess {

    private final ProjectVersionRepository versions;

    ProjectAccessService(ProjectVersionRepository versions) {
        this.versions = versions;
    }

    @Override
    public VersionEditability editabilityOf(long userId, long versionId) {
        return versions.findAccess(versionId)
                .map(access -> {
                    if (access.ownerId() != userId) {
                        return VersionEditability.NOT_OWNER;
                    }
                    return access.recorded() ? VersionEditability.ALREADY_RECORDED : VersionEditability.EDITABLE;
                })
                .orElse(VersionEditability.NOT_FOUND);
    }
}
