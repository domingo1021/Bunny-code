package com.bunnycode.battle.domain;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * State changes are compare-and-set {@code UPDATE}s that return the number of rows changed: the
 * caller acts (broadcasts "started", "finished"…) only when it got 1, so concurrent events — two
 * players getting ready at once, two accepted submissions — produce exactly one outcome.
 */
public interface BattleRepository extends JpaRepository<Battle, Long> {

    String SUMMARY = """
            select new com.bunnycode.battle.domain.BattleSummaryRow(
                b.id, b.name, b.status, b.watchCount, b.firstPlayerId, b.secondPlayerId, b.winnerId, q.name, q.level)
            from Battle b join b.question q
            """;

    boolean existsByName(String name);

    @Query("select b from Battle b join fetch b.question where b.id = :id")
    Optional<Battle> findWithQuestion(@Param("id") long id);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Battle b set b.status = :to where b.id = :id and b.status = :from")
    int transition(@Param("id") long id, @Param("from") BattleStatus from, @Param("to") BattleStatus to);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Battle b set b.status = :finished, b.winnerId = :winnerId
            where b.id = :id and b.status = :running
            """)
    int finish(@Param("id") long id, @Param("winnerId") long winnerId,
               @Param("running") BattleStatus running, @Param("finished") BattleStatus finished);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Battle b set b.winnerCodeKey = :key where b.id = :id")
    int attachWinnerCode(@Param("id") long id, @Param("key") String key);

    @Transactional
    @Modifying
    @Query("update Battle b set b.watchCount = b.watchCount + 1 where b.id = :id")
    int incrementWatchCount(@Param("id") long id);

    @Query(value = SUMMARY + """
            where b.status in :statuses and (:level is null or q.level = :level)
            order by b.watchCount desc, b.id desc
            """,
            countQuery = "select count(b) from Battle b join b.question q where b.status in :statuses and (:level is null or q.level = :level)")
    Page<BattleSummaryRow> findByStatuses(@Param("statuses") Collection<BattleStatus> statuses,
                                          @Param("level") QuestionLevel level, Pageable pageable);

    @Query(value = SUMMARY + """
            where b.status in :statuses and (:level is null or q.level = :level) and b.name like :pattern escape '\\'
            order by b.watchCount desc, b.id desc
            """,
            countQuery = """
            select count(b) from Battle b join b.question q
            where b.status in :statuses and (:level is null or q.level = :level) and b.name like :pattern escape '\\'
            """)
    Page<BattleSummaryRow> searchByName(@Param("statuses") Collection<BattleStatus> statuses,
                                        @Param("level") QuestionLevel level,
                                        @Param("pattern") String pattern, Pageable pageable);

    @Query(value = SUMMARY + """
            where b.status in :statuses and (:level is null or q.level = :level) and q.name like :pattern escape '\\'
            order by b.watchCount desc, b.id desc
            """,
            countQuery = """
            select count(b) from Battle b join b.question q
            where b.status in :statuses and (:level is null or q.level = :level) and q.name like :pattern escape '\\'
            """)
    Page<BattleSummaryRow> searchByQuestion(@Param("statuses") Collection<BattleStatus> statuses,
                                            @Param("level") QuestionLevel level,
                                            @Param("pattern") String pattern, Pageable pageable);

    @Query(value = SUMMARY + """
            where b.status in :statuses and (:level is null or q.level = :level)
              and (b.firstPlayerId in :playerIds or b.secondPlayerId in :playerIds)
            order by b.watchCount desc, b.id desc
            """,
            countQuery = """
            select count(b) from Battle b join b.question q
            where b.status in :statuses and (:level is null or q.level = :level)
              and (b.firstPlayerId in :playerIds or b.secondPlayerId in :playerIds)
            """)
    Page<BattleSummaryRow> findByPlayers(@Param("statuses") Collection<BattleStatus> statuses,
                                         @Param("level") QuestionLevel level,
                                         @Param("playerIds") Collection<Long> playerIds, Pageable pageable);

    @Query("select count(b) from Battle b where b.status in :statuses")
    long countByStatuses(@Param("statuses") Collection<BattleStatus> statuses);
}
