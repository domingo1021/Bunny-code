package com.bunnycode.project.application;

import com.bunnycode.project.application.VersionView.FileView;
import com.bunnycode.project.domain.Project;
import com.bunnycode.project.domain.Project.NewProject;
import com.bunnycode.project.domain.ProjectFile;
import com.bunnycode.project.domain.ProjectRepository;
import com.bunnycode.project.domain.ProjectStarRepository;
import com.bunnycode.project.domain.ProjectVersion;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.ratelimit.RateLimiter;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.storage.StorageLayout;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectCommandService {

    /** One counted view per viewer per project per hour (v1 counted every page load). */
    static final Duration VIEW_WINDOW = Duration.ofHours(1);

    private final ProjectRepository projects;
    private final ProjectStarRepository stars;
    private final StorageLayout storageLayout;
    private final FileStorage storage;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    ProjectCommandService(ProjectRepository projects, ProjectStarRepository stars, StorageLayout storageLayout,
                          FileStorage storage, RateLimiter rateLimiter, Clock clock) {
        this.projects = projects;
        this.stars = stars;
        this.storageLayout = storageLayout;
        this.storage = storage;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public ProjectReference create(AuthenticatedUser owner, NewProject spec) {
        if (projects.existsByName(spec.name())) {
            throw nameTaken();
        }
        Project project = Project.create(spec, owner.id(), storageLayout.defaultTemplateKey(), clock.instant());
        try {
            projects.saveAndFlush(project);
        } catch (DataIntegrityViolationException e) {
            // Concurrent creation, or the name belongs to a soft-deleted project.
            throw nameTaken();
        }
        return new ProjectReference(project.getId(), project.getName());
    }

    /** Branches a new version from the latest one. The project row is locked while numbering. */
    public VersionView branchVersion(AuthenticatedUser user, String projectName, String versionName, String fileName) {
        Project project = projects.findByNameForUpdate(projectName)
                .filter(candidate -> candidate.isVisibleTo(Optional.of(user.id())))
                .orElseThrow(ProjectErrors::projectNotFound);
        if (!project.isOwnedBy(user.id())) {
            throw ProjectErrors.notOwner();
        }
        ProjectVersion version = project.branchVersion(versionName, fileName, clock.instant());
        projects.flush(); // assigns ids for the response
        ProjectFile file = version.currentFile().orElseThrow();
        return new VersionView(version.getId(), version.getName(), version.getNumber(),
                new FileView(file.getId(), file.getName(), storage.publicUrl(file.getStorageKey()), file.getRevision()),
                null);
    }

    public void star(AuthenticatedUser user, String projectName) {
        Project project = visibleProject(projectName, Optional.of(user));
        if (stars.insertIfAbsent(project.getId(), user.id(), clock.instant()) == 1) {
            projects.adjustStarCount(project.getId(), 1);
        }
    }

    public void unstar(AuthenticatedUser user, String projectName) {
        Project project = visibleProject(projectName, Optional.of(user));
        if (stars.deleteByProjectAndUser(project.getId(), user.id()) == 1) {
            projects.adjustStarCount(project.getId(), -1);
        }
    }

    /**
     * Counts a view unless the owner is looking or this viewer was already counted within the
     * window. Anonymous viewers are keyed by client address.
     */
    public void recordView(String projectName, Optional<AuthenticatedUser> viewer, String clientAddress) {
        Project project = visibleProject(projectName, viewer);
        if (viewer.map(user -> project.isOwnedBy(user.id())).orElse(false)) {
            return;
        }
        String viewerKey = viewer.map(user -> "user:" + user.id()).orElse("addr:" + clientAddress);
        if (rateLimiter.tryAcquire("project-view:" + project.getId() + ":" + viewerKey, 1, VIEW_WINDOW)) {
            projects.incrementWatchCount(project.getId());
        }
    }

    private Project visibleProject(String projectName, Optional<AuthenticatedUser> viewer) {
        return projects.findByName(projectName)
                .filter(candidate -> candidate.isVisibleTo(viewer.map(AuthenticatedUser::id)))
                .orElseThrow(ProjectErrors::projectNotFound);
    }

    private static BunnyException nameTaken() {
        return BunnyException.conflict("project.name-taken", "That project name is already taken.");
    }
}
