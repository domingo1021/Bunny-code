package com.bunnycode.project.domain;

import com.bunnycode.project.domain.ProjectRows.VersionAccessRow;
import com.bunnycode.project.domain.ProjectRows.VersionFileRow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only queries over versions; versions are written through the {@link Project} aggregate. */
public interface ProjectVersionRepository extends Repository<ProjectVersion, Long> {

    /**
     * All versions of a project with their current file, in <b>one</b> query (a left join with
     * the "not superseded" condition in the {@code ON} clause), instead of one query per version.
     * Rows are ordered so that, if a version ever has several current files, the newest is first.
     */
    @Query("""
            select new com.bunnycode.project.domain.ProjectRows$VersionFileRow(
                v.id, v.name, v.number, f.id, f.name, f.storageKey, f.revision)
            from ProjectVersion v
            left join v.files f on f.superseded = false
            where v.project.id = :projectId
            order by v.number asc, f.id desc
            """)
    List<VersionFileRow> findVersionFiles(@Param("projectId") long projectId);

    Optional<ProjectVersion> findByProject_IdAndNumber(Long projectId, int number);

    @Query("""
            select new com.bunnycode.project.domain.ProjectRows$VersionAccessRow(
                v.id, p.ownerId,
                case when exists (select r.id from Recording r where r.version = v) then true else false end)
            from ProjectVersion v
            join v.project p
            where v.id = :versionId
            """)
    Optional<VersionAccessRow> findAccess(@Param("versionId") long versionId);
}
