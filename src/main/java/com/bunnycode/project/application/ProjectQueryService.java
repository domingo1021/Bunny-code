package com.bunnycode.project.application;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.bunnycode.identity.UserDirectory;
import com.bunnycode.identity.UserSummary;
import com.bunnycode.project.application.VersionView.FileView;
import com.bunnycode.project.domain.Project;
import com.bunnycode.project.domain.ProjectRepository;
import com.bunnycode.project.domain.ProjectRows.ProjectSummaryRow;
import com.bunnycode.project.domain.ProjectRows.RecordingRow;
import com.bunnycode.project.domain.ProjectRows.VersionFileRow;
import com.bunnycode.project.domain.ProjectStar;
import com.bunnycode.project.domain.ProjectStarRepository;
import com.bunnycode.project.domain.ProjectVersionRepository;
import com.bunnycode.project.domain.RecordingRepository;
import com.bunnycode.project.domain.Visibility;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.web.LikePatterns;
import com.bunnycode.shared.web.PageResponse;
import com.bunnycode.storage.FileStorage;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the project module. Every method issues a fixed number of queries regardless of
 * how many rows it returns — owners are resolved in one batched lookup per page.
 */
@Service
@Transactional(readOnly = true)
public class ProjectQueryService {

    /** Bounds the IN-list built from a user-name search. */
    private static final int MAX_OWNER_MATCHES = 100;

    private final ProjectRepository projects;
    private final ProjectVersionRepository versions;
    private final RecordingRepository recordings;
    private final ProjectStarRepository stars;
    private final UserDirectory users;
    private final FileStorage storage;

    ProjectQueryService(ProjectRepository projects, ProjectVersionRepository versions, RecordingRepository recordings,
                        ProjectStarRepository stars, UserDirectory users, FileStorage storage) {
        this.projects = projects;
        this.versions = versions;
        this.recordings = recordings;
        this.stars = stars;
        this.users = users;
        this.storage = storage;
    }

    /** Public projects; {@code keyword} matches project name, description or owner name. */
    public PageResponse<ProjectSummaryView> publicProjects(String keyword, ProjectSort sort, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ProjectSummaryRow> rows;
        if (keyword != null && !keyword.isBlank()) {
            Set<Long> matchingOwners = users.findIdsByNameContaining(keyword, MAX_OWNER_MATCHES);
            rows = projects.search(Visibility.PUBLIC, LikePatterns.contains(keyword), matchingOwners, pageRequest);
        } else if (sort == ProjectSort.POPULAR) {
            rows = projects.findPopular(Visibility.PUBLIC, pageRequest);
        } else if (sort == ProjectSort.STARRED) {
            rows = projects.findStarred(Visibility.PUBLIC, pageRequest);
        } else {
            rows = projects.findRecent(Visibility.PUBLIC, pageRequest);
        }
        return withOwners(rows);
    }

    /** A user's projects: all of them for the owner, public ones for everybody else. */
    public PageResponse<ProjectSummaryView> projectsOf(long ownerId, Optional<AuthenticatedUser> viewer,
                                                       String keyword, int page, int size) {
        boolean ownerIsViewing = viewer.map(user -> user.id() == ownerId).orElse(false);
        Set<Visibility> visible = ownerIsViewing ? EnumSet.allOf(Visibility.class) : EnumSet.of(Visibility.PUBLIC);
        return withOwners(projects.findOwnedBy(ownerId, visible, LikePatterns.contains(keyword), PageRequest.of(page, size)));
    }

    public ProjectDetailView detail(String projectName, Optional<AuthenticatedUser> viewer) {
        Project project = projects.findByName(projectName)
                .filter(candidate -> candidate.isVisibleTo(viewer.map(AuthenticatedUser::id)))
                .orElseThrow(ProjectErrors::projectNotFound);

        List<VersionView> versionViews = versionsOf(project.getId());
        UserSummary owner = users.findById(project.getOwnerId())
                .orElseThrow(() -> new IllegalStateException("Owner missing for project " + project.getId()));
        boolean starred = viewer
                .map(user -> stars.existsById(new ProjectStar.Key(project.getId(), user.id())))
                .orElse(false);

        return new ProjectDetailView(project.getId(), project.getName(), project.getDescription(),
                project.getVisibility(), project.getWatchCount(), project.getStarCount(), project.getCreatedAt(),
                owner, starred, versionViews);
    }

    /** Headline numbers for the home page: public projects and their recorded sessions. */
    public ProjectStats stats() {
        return new ProjectStats(projects.countByVisibility(Visibility.PUBLIC),
                projects.countRecordings(Visibility.PUBLIC));
    }

    public record ProjectStats(long projects, long recordings) {
    }

    private List<VersionView> versionsOf(long projectId) {
        // Query 1: every version with its current file. First row per version wins (newest file).
        Map<Long, VersionFileRow> byVersion = new LinkedHashMap<>();
        versions.findVersionFiles(projectId).forEach(row -> byVersion.putIfAbsent(row.versionId(), row));
        if (byVersion.isEmpty()) {
            return List.of();
        }
        // Query 2: all their recordings at once.
        Map<Long, RecordingRow> recordingByVersion = recordings.findRowsByVersionIds(byVersion.keySet()).stream()
                .collect(toMap(RecordingRow::versionId, Function.identity(), (first, second) -> first));

        return byVersion.values().stream()
                .map(row -> new VersionView(row.versionId(), row.versionName(), row.versionNumber(),
                        fileOf(row), recordingOf(recordingByVersion.get(row.versionId()))))
                .toList();
    }

    private FileView fileOf(VersionFileRow row) {
        if (row.fileId() == null) {
            return null;
        }
        return new FileView(row.fileId(), row.fileName(), storage.publicUrl(row.fileKey()), row.fileRevision());
    }

    private RecordingSummaryView recordingOf(RecordingRow row) {
        if (row == null) {
            return null;
        }
        return new RecordingSummaryView(row.id(), storage.publicUrl(row.baseFileKey()), row.startedAt(), row.endedAt());
    }

    private PageResponse<ProjectSummaryView> withOwners(Page<ProjectSummaryRow> rows) {
        Set<Long> ownerIds = rows.stream().map(ProjectSummaryRow::ownerId).collect(toSet());
        Map<Long, UserSummary> owners = users.findAllByIds(ownerIds);
        return PageResponse.of(rows).map(row -> new ProjectSummaryView(row.id(), row.name(), row.description(),
                row.visibility(), row.watchCount(), row.starCount(), row.createdAt(), row.latestVersionName(),
                owners.get(row.ownerId())));
    }
}
