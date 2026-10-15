package com.bunnycode.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only user lookups for other modules. Modules reference users by id only and resolve
 * display data through this interface — in one batched call per page, never once per row.
 */
public interface UserDirectory {

    Optional<UserSummary> findById(long userId);

    /** Returns the users that exist among {@code userIds}; unknown ids are simply absent. */
    Map<Long, UserSummary> findAllByIds(Collection<Long> userIds);

    /** Ids of users whose name contains {@code fragment} (case-insensitive), at most {@code limit}. */
    Set<Long> findIdsByNameContaining(String fragment, int limit);
}
