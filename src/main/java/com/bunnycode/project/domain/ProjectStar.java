package com.bunnycode.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** A user's star on a project — at most one per (project, user). */
@Entity
@Table(name = "project_star")
public class ProjectStar {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectStar() {
        // for JPA; rows are written by an idempotent native insert in ProjectStarRepository
    }

    public Key getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public record Key(
            @Column(name = "project_id", columnDefinition = "int unsigned") Long projectId,
            @Column(name = "user_id", columnDefinition = "int unsigned") Long userId) implements Serializable {
    }
}
