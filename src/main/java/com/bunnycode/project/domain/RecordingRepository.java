package com.bunnycode.project.domain;

import com.bunnycode.project.domain.ProjectRows.RecordingRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    /** Recordings for many versions in one query; {@code r.version.id} reads the FK without a join. */
    @Query("""
            select new com.bunnycode.project.domain.ProjectRows$RecordingRow(
                r.id, r.version.id, r.baseFileKey, r.startedAt, r.endedAt)
            from Recording r
            where r.version.id in :versionIds
            """)
    List<RecordingRow> findRowsByVersionIds(@Param("versionIds") Collection<Long> versionIds);

    boolean existsByVersion_Id(Long versionId);

    Optional<Recording> findByVersion_Id(Long versionId);
}
