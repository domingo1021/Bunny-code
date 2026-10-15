package com.bunnycode.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;

/** One saved revision of a version's source file; the content lives in object storage. */
@Entity
@Table(name = "file")
@SQLRestriction("deleted = 0")
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "file_name", nullable = false, length = 30)
    private String name;

    @Column(name = "file_url", nullable = false)
    private String storageKey;

    /** Epoch-millis string ({@code log} in v1). */
    @Column(name = "log", nullable = false, length = 13, columnDefinition = "char(13)")
    private String revision;

    /** {@code hided} in v1: a newer revision replaced this one. */
    @Column(name = "hided", nullable = false)
    private boolean superseded;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, columnDefinition = "int unsigned")
    private ProjectVersion version;

    protected ProjectFile() {
        // for JPA
    }

    ProjectFile(ProjectVersion version, String name, String storageKey, String revision) {
        this.version = Objects.requireNonNull(version, "version");
        this.name = Objects.requireNonNull(name, "name");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.revision = Objects.requireNonNull(revision, "revision");
    }

    void supersede() {
        this.superseded = true;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getRevision() {
        return revision;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public ProjectVersion getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProjectFile that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return ProjectFile.class.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectFile[id=" + id + ", name=" + name + ", revision=" + revision + "]";
    }
}
