package com.bunnycode.project.domain;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProjectStarRepository extends Repository<ProjectStar, ProjectStar.Key> {

    boolean existsById(ProjectStar.Key id);

    /**
     * Idempotent and race-free: returns 1 only for the request that actually created the star,
     * so the caller increments the counter exactly once even under concurrent double-clicks.
     */
    @Modifying
    @Query(value = "insert ignore into project_star (project_id, user_id, created_at) values (:projectId, :userId, :now)",
            nativeQuery = true)
    int insertIfAbsent(@Param("projectId") long projectId, @Param("userId") long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from ProjectStar s where s.id.projectId = :projectId and s.id.userId = :userId")
    int deleteByProjectAndUser(@Param("projectId") long projectId, @Param("userId") long userId);
}
