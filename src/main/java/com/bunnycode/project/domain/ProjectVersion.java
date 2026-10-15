package com.bunnycode.project.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.hibernate.annotations.SQLRestriction;

/**
 * A named snapshot of a project. Its recording is deliberately <em>not</em> mapped here: an
 * inverse {@code @OneToOne} cannot be lazy in Hibernate and would add one query per version.
 * Recordings are looked up by version id in a single batched query instead.
 */
@Entity
@Table(name = "version")
@SQLRestriction("deleted = 0")
public class ProjectVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "version_name", nullable = false, length = 30)
    private String name;

    @Column(name = "version_number", nullable = false, columnDefinition = "int unsigned")
    private int number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, columnDefinition = "int unsigned")
    private Project project;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProjectFile> files = new ArrayList<>();

    protected ProjectVersion() {
        // for JPA
    }

    ProjectVersion(Project project, String name, int number) {
        this.project = Objects.requireNonNull(project, "project");
        this.name = Objects.requireNonNull(name, "name");
        this.number = number;
    }

    void addFile(String fileName, String storageKey, String revision) {
        files.add(new ProjectFile(this, fileName, storageKey, revision));
    }

    /**
     * Replaces the current file with a newly saved revision. Older revisions stay in the table
     * (flagged as superseded), exactly as v1 kept them.
     */
    public ProjectFile saveNewRevision(String fileName, String storageKey, String revision) {
        files.forEach(ProjectFile::supersede);
        var saved = new ProjectFile(this, fileName, storageKey, revision);
        files.add(saved);
        return saved;
    }

    /** The newest file that has not been superseded. */
    public Optional<ProjectFile> currentFile() {
        return files.reversed().stream().filter(file -> !file.isSuperseded()).findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public Project getProject() {
        return project;
    }

    public List<ProjectFile> getFiles() {
        return Collections.unmodifiableList(files);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProjectVersion that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ProjectVersion.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectVersion[id=" + id + ", name=" + name + ", number=" + number + "]";
    }
}
