package com.bunnycode.project.domain;

import static java.util.Comparator.comparingInt;

import com.bunnycode.shared.error.BunnyException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.SQLRestriction;

/**
 * Aggregate root: a project with its versions and their files.
 *
 * <p>The owner is referenced by id, not by an association to the identity module's entity —
 * modules share ids, never entity graphs. Counters are not mutated through the entity: they are
 * incremented by atomic {@code UPDATE}s in {@link ProjectRepository} so concurrent views never
 * lose updates.
 */
@Entity
@Table(name = "project")
@SQLRestriction("deleted = 0")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "project_name", nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "project_description", nullable = false, length = 50)
    private String description;

    @Convert(converter = VisibilityConverter.class)
    @Column(name = "is_public", nullable = false)
    private Visibility visibility;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "int unsigned")
    private Long ownerId;

    @Column(name = "watch_count", nullable = false, insertable = false, updatable = false, columnDefinition = "int unsigned")
    private long watchCount;

    @Column(name = "star_count", nullable = false, insertable = false, updatable = false, columnDefinition = "int unsigned")
    private long starCount;

    @Column(name = "create_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number ASC")
    private List<ProjectVersion> versions = new ArrayList<>();

    protected Project() {
        // for JPA
    }

    private Project(String name, String description, Visibility visibility, long ownerId, Instant createdAt) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.ownerId = ownerId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new project always starts with version 1 holding one file copied from {@code templateKey}. */
    public static Project create(NewProject spec, long ownerId, String templateKey, Instant now) {
        var project = new Project(spec.name(), spec.description(), spec.visibility(), ownerId, now);
        var first = new ProjectVersion(project, spec.versionName(), 1);
        first.addFile(spec.fileName(), templateKey, revisionOf(now));
        project.versions.add(first);
        return project;
    }

    /**
     * Branches a new version from the latest one: same content, new name, next number.
     *
     * @throws BunnyException if the name is taken or the latest version has no file to copy
     */
    public ProjectVersion branchVersion(String versionName, String fileName, Instant now) {
        if (versions.stream().anyMatch(version -> version.getName().equals(versionName))) {
            throw BunnyException.conflict("version.name-taken", "This project already has a version with that name.");
        }
        ProjectVersion latest = versions.stream()
                .max(comparingInt(ProjectVersion::getNumber))
                .orElseThrow(() -> BunnyException.conflict("version.no-base", "This project has no version to branch from."));
        ProjectFile base = latest.currentFile()
                .orElseThrow(() -> BunnyException.conflict("version.no-base", "The latest version has no file to branch from."));
        var branched = new ProjectVersion(this, versionName, latest.getNumber() + 1);
        branched.addFile(fileName, base.getStorageKey(), revisionOf(now));
        versions.add(branched);
        return branched;
    }

    public Optional<ProjectVersion> findVersion(int number) {
        return versions.stream().filter(version -> version.getNumber() == number).findFirst();
    }

    public boolean isOwnedBy(long userId) {
        return ownerId == userId;
    }

    /** Private projects exist only for their owner; everyone else gets "not found". */
    public boolean isVisibleTo(Optional<Long> viewerId) {
        return visibility == Visibility.PUBLIC || viewerId.map(this::isOwnedBy).orElse(false);
    }

    /** v1 stored the creation instant in milliseconds as the file "log"; kept for compatibility. */
    static String revisionOf(Instant instant) {
        return String.valueOf(instant.toEpochMilli());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public long getWatchCount() {
        return watchCount;
    }

    public long getStarCount() {
        return starCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ProjectVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Project that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Project.class.hashCode();
    }

    @Override
    public String toString() {
        return "Project[id=" + id + ", name=" + name + "]";
    }

    /** What a user asks for when creating a project. */
    public record NewProject(String name, String description, Visibility visibility, String versionName, String fileName) {

        public NewProject {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(versionName, "versionName");
            Objects.requireNonNull(fileName, "fileName");
        }
    }
}
