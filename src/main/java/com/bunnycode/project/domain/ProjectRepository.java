package com.bunnycode.project.domain;

import com.bunnycode.project.domain.ProjectRows.ProjectSummaryRow;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Page queries return {@link ProjectSummaryRow} projections with an explicit count query, so
 * paging is done in SQL ({@code LIMIT/OFFSET}) and never in memory. Patterns passed as
 * {@code pattern} must be built with {@code LikePatterns} (they are escaped with {@code \}).
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    String SUMMARY = """
            select new com.bunnycode.project.domain.ProjectRows$ProjectSummaryRow(
                p.id, p.name, p.description, p.visibility, p.watchCount, p.starCount, p.createdAt, p.ownerId,
                (select v.name from ProjectVersion v
                 where v.project = p
                   and v.number = (select max(latest.number) from ProjectVersion latest where latest.project = p)))
            from Project p
            """;

    Optional<Project> findByName(String name);

    boolean existsByName(String name);

    /**
     * {@code SELECT … FOR UPDATE}: serialises writers of the same project (version numbering,
     * one recording per version) without a table-wide lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.name = :name")
    Optional<Project> findByNameForUpdate(@Param("name") String name);

    @Query(value = SUMMARY + """
            where p.visibility = :visibility
            order by p.createdAt desc, p.id desc
            """,
            countQuery = "select count(p) from Project p where p.visibility = :visibility")
    Page<ProjectSummaryRow> findRecent(@Param("visibility") Visibility visibility, Pageable pageable);

    @Query(value = SUMMARY + """
            where p.visibility = :visibility
            order by p.watchCount desc, p.id desc
            """,
            countQuery = "select count(p) from Project p where p.visibility = :visibility")
    Page<ProjectSummaryRow> findPopular(@Param("visibility") Visibility visibility, Pageable pageable);

    @Query(value = SUMMARY + """
            where p.visibility = :visibility
            order by p.starCount desc, p.id desc
            """,
            countQuery = "select count(p) from Project p where p.visibility = :visibility")
    Page<ProjectSummaryRow> findStarred(@Param("visibility") Visibility visibility, Pageable pageable);

    /** Matches name, description, or an owner among {@code ownerIds} (resolved from the user name beforehand). */
    @Query(value = SUMMARY + """
            where p.visibility = :visibility
              and (p.name like :pattern escape '\\'
                   or p.description like :pattern escape '\\'
                   or p.ownerId in :ownerIds)
            order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p) from Project p
            where p.visibility = :visibility
              and (p.name like :pattern escape '\\'
                   or p.description like :pattern escape '\\'
                   or p.ownerId in :ownerIds)
            """)
    Page<ProjectSummaryRow> search(@Param("visibility") Visibility visibility, @Param("pattern") String pattern,
                                   @Param("ownerIds") Collection<Long> ownerIds, Pageable pageable);

    @Query(value = SUMMARY + """
            where p.ownerId = :ownerId
              and p.visibility in :visibilities
              and (p.name like :pattern escape '\\' or p.description like :pattern escape '\\')
            order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p) from Project p
            where p.ownerId = :ownerId
              and p.visibility in :visibilities
              and (p.name like :pattern escape '\\' or p.description like :pattern escape '\\')
            """)
    Page<ProjectSummaryRow> findOwnedBy(@Param("ownerId") long ownerId,
                                        @Param("visibilities") Collection<Visibility> visibilities,
                                        @Param("pattern") String pattern, Pageable pageable);

    long countByVisibility(Visibility visibility);

    /** Recorded versions of projects with the given visibility. */
    @Query("""
            select count(r) from Recording r
            join r.version v
            join v.project p
            where p.visibility = :visibility
            """)
    long countRecordings(@Param("visibility") Visibility visibility);

    /** Atomic increment — no read-modify-write, so concurrent viewers never overwrite each other. */
    @Modifying
    @Query("update Project p set p.watchCount = p.watchCount + 1 where p.id = :id")
    int incrementWatchCount(@Param("id") long id);

    @Modifying
    @Query("update Project p set p.starCount = p.starCount + :delta where p.id = :id")
    int adjustStarCount(@Param("id") long id, @Param("delta") int delta);
}
