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
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;

/**
 * Metadata of a recorded coding session (one per version). The keystroke events themselves live
 * in the time-series store; {@code baseFileKey} is the content the replay starts from.
 */
@Entity
@Table(name = "record")
@SQLRestriction("deleted = 0")
public class Recording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id", columnDefinition = "int unsigned")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, columnDefinition = "int unsigned")
    private ProjectVersion version;

    @Column(name = "base_url", nullable = false)
    private String baseFileKey;

    @Column(name = "start_time", nullable = false, columnDefinition = "datetime")
    private Instant startedAt;

    @Column(name = "end_time", nullable = false, columnDefinition = "datetime")
    private Instant endedAt;

    protected Recording() {
        // for JPA
    }

    private Recording(ProjectVersion version, String baseFileKey, Instant startedAt, Instant endedAt) {
        this.version = Objects.requireNonNull(version, "version");
        this.baseFileKey = Objects.requireNonNull(baseFileKey, "baseFileKey");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.endedAt = Objects.requireNonNull(endedAt, "endedAt");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Recording ends before it starts: " + startedAt + " > " + endedAt);
        }
    }

    public static Recording of(ProjectVersion version, String baseFileKey, Instant startedAt, Instant endedAt) {
        return new Recording(version, baseFileKey, startedAt, endedAt);
    }

    public Long getId() {
        return id;
    }

    public ProjectVersion getVersion() {
        return version;
    }

    public String getBaseFileKey() {
        return baseFileKey;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Recording that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Recording.class.hashCode();
    }

    @Override
    public String toString() {
        return "Recording[id=" + id + ", startedAt=" + startedAt + ", endedAt=" + endedAt + "]";
    }
}
