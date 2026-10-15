package com.bunnycode.identity.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** The column collation is case-insensitive, so v1's mixed-case addresses still match. */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByName(String name);

    /** One query for any number of ids (IN-list padded by Hibernate to reuse statements). */
    @Query("""
            select new com.bunnycode.identity.domain.UserSummaryRow(u.id, u.name, u.pictureKey)
            from UserAccount u
            where u.id in :ids
            """)
    List<UserSummaryRow> findSummariesByIdIn(@Param("ids") Collection<Long> ids);

    /** Derived query: Spring Data escapes LIKE wildcards in {@code fragment}. */
    List<UserIdRow> findByNameContainingIgnoreCase(String fragment, Limit limit);

    record UserIdRow(Long id) {
    }
}
