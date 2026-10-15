package com.bunnycode.battle.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends Repository<Question, Long> {

    @Query("select q.id from Question q where q.level = :level")
    List<Long> findIdsByLevel(@Param("level") QuestionLevel level);

    Optional<Question> findById(Long id);

    /** The question and all its test cases in one query (judging needs both, every time). */
    @Query("select q from Question q left join fetch q.testCases where q.id = :id")
    Optional<Question> findWithTestCases(@Param("id") long id);
}
