package com.bunnycode.project.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bunnycode.project.application.RecordingView.RecordedEventView;
import com.bunnycode.project.application.VersionView.FileView;
import com.bunnycode.project.domain.CodingEvent;
import com.bunnycode.project.domain.Project;
import com.bunnycode.project.domain.ProjectFile;
import com.bunnycode.project.domain.ProjectRepository;
import com.bunnycode.project.domain.ProjectVersion;
import com.bunnycode.project.domain.ProjectVersionRepository;
import com.bunnycode.project.domain.Recording;
import com.bunnycode.project.domain.RecordingRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.storage.StorageLayout;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves and replays coding sessions.
 *
 * <p>Saving touches three stores. The object upload and the event write happen <em>between</em>
 * two short database transactions so that no connection is held during network I/O; the second
 * transaction re-checks "one recording per version" under a row lock. If that final check loses
 * a race, the uploaded file and events are orphaned but invisible (nothing references them).
 */
@Service
public class RecordingService {

    /** v1 stored session bounds padded by 1,000,000 ms; widen reads so its events are found. */
    static final Duration LEGACY_READ_MARGIN = Duration.ofMinutes(20);
    private static final String CODE_CONTENT_TYPE = "application/javascript";

    private final ProjectRepository projects;
    private final ProjectVersionRepository versions;
    private final RecordingRepository recordings;
    private final CodingEventStore eventStore;
    private final FileStorage storage;
    private final StorageLayout storageLayout;
    private final RecordingProperties properties;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    RecordingService(ProjectRepository projects, ProjectVersionRepository versions, RecordingRepository recordings,
                     CodingEventStore eventStore, FileStorage storage, StorageLayout storageLayout,
                     RecordingProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.projects = projects;
        this.versions = versions;
        this.recordings = recordings;
        this.eventStore = eventStore;
        this.storage = storage;
        this.storageLayout = storageLayout;
        this.properties = properties;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public SavedRecordingView save(AuthenticatedUser user, String projectName, int versionNumber,
                                   List<CodingEvent> events, String finalCode) {
        if (events.isEmpty()) {
            throw BunnyException.invalid("recording.empty", "There is nothing to save yet — write some code first.");
        }
        if (events.size() > properties.maxEventsPerSession()) {
            throw BunnyException.invalid("recording.too-long",
                    "This session is too long to save (max " + properties.maxEventsPerSession() + " edits).");
        }

        SaveTarget target = readTransaction.execute(status -> saveTarget(user, projectName, versionNumber));
        String revision = String.valueOf(clock.millis());
        String newKey = storageLayout.codeFileKey(user.id(), target.projectId(), target.versionId(), revision, target.fileName());
        storage.store(newKey, finalCode.getBytes(UTF_8), CODE_CONTENT_TYPE);
        eventStore.append(target.projectId(), target.versionId(), target.fileId(), events);

        Instant startedAt = events.stream().map(CodingEvent::timestamp).min(Comparator.naturalOrder()).orElseThrow();
        Instant endedAt = events.stream().map(CodingEvent::timestamp).max(Comparator.naturalOrder()).orElseThrow();
        return writeTransaction.execute(status ->
                commit(projectName, versionNumber, target, newKey, revision, startedAt, endedAt));
    }

    public RecordingView recordingOf(Optional<AuthenticatedUser> viewer, String projectName, int versionNumber) {
        ReplayTarget target = readTransaction.execute(status -> replayTarget(viewer, projectName, versionNumber));
        List<RecordedEvent> events = eventStore.read(target.projectId(), target.versionId(),
                target.startedAt().minus(LEGACY_READ_MARGIN), target.endedAt().plus(LEGACY_READ_MARGIN));
        EventFormat format = events.stream().anyMatch(event -> event.format() == EventFormat.LEGACY)
                ? EventFormat.LEGACY : EventFormat.V2;
        List<RecordedEventView> views = events.stream()
                .map(event -> new RecordedEventView(event.action(), event.line(), event.index(), event.code(),
                        event.timestamp().toEpochMilli()))
                .toList();
        return new RecordingView(target.recordingId(), storage.publicUrl(target.baseFileKey()),
                target.startedAt(), target.endedAt(), format, views);
    }

    private SaveTarget saveTarget(AuthenticatedUser user, String projectName, int versionNumber) {
        Project project = projects.findByName(projectName)
                .filter(candidate -> candidate.isVisibleTo(Optional.of(user.id())))
                .orElseThrow(ProjectErrors::projectNotFound);
        if (!project.isOwnedBy(user.id())) {
            throw ProjectErrors.notOwner();
        }
        ProjectVersion version = versions.findByProject_IdAndNumber(project.getId(), versionNumber)
                .orElseThrow(ProjectErrors::versionNotFound);
        if (recordings.existsByVersion_Id(version.getId())) {
            throw alreadyRecorded();
        }
        ProjectFile current = version.currentFile()
                .orElseThrow(() -> BunnyException.conflict("version.no-file", "This version has no file to record."));
        return new SaveTarget(project.getId(), version.getId(), current.getId(), current.getName(), current.getStorageKey());
    }

    private SavedRecordingView commit(String projectName, int versionNumber, SaveTarget target, String newKey,
                                      String revision, Instant startedAt, Instant endedAt) {
        Project project = projects.findByNameForUpdate(projectName).orElseThrow(ProjectErrors::projectNotFound);
        ProjectVersion version = project.findVersion(versionNumber).orElseThrow(ProjectErrors::versionNotFound);
        if (recordings.existsByVersion_Id(version.getId())) {
            throw alreadyRecorded();
        }
        ProjectFile saved = version.saveNewRevision(target.fileName(), newKey, revision);
        Recording recording = recordings.save(Recording.of(version, target.baseFileKey(), startedAt, endedAt));
        projects.flush();
        return new SavedRecordingView(
                new RecordingSummaryView(recording.getId(), storage.publicUrl(recording.getBaseFileKey()),
                        recording.getStartedAt(), recording.getEndedAt()),
                new FileView(saved.getId(), saved.getName(), storage.publicUrl(saved.getStorageKey()), saved.getRevision()));
    }

    private ReplayTarget replayTarget(Optional<AuthenticatedUser> viewer, String projectName, int versionNumber) {
        Project project = projects.findByName(projectName)
                .filter(candidate -> candidate.isVisibleTo(viewer.map(AuthenticatedUser::id)))
                .orElseThrow(ProjectErrors::projectNotFound);
        ProjectVersion version = versions.findByProject_IdAndNumber(project.getId(), versionNumber)
                .orElseThrow(ProjectErrors::versionNotFound);
        Recording recording = recordings.findByVersion_Id(version.getId())
                .orElseThrow(() -> BunnyException.notFound("recording.not-found", "This version has not been recorded yet."));
        return new ReplayTarget(project.getId(), version.getId(), recording.getId(), recording.getBaseFileKey(),
                recording.getStartedAt(), recording.getEndedAt());
    }

    private static BunnyException alreadyRecorded() {
        return BunnyException.conflict("recording.exists",
                "This version is already recorded. Create a new version to keep coding.");
    }

    private record SaveTarget(long projectId, long versionId, long fileId, String fileName, String baseFileKey) {
    }

    private record ReplayTarget(long projectId, long versionId, long recordingId, String baseFileKey,
                                Instant startedAt, Instant endedAt) {
    }
}
