package com.bunnycode.identity.application;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.bunnycode.identity.UserDirectory;
import com.bunnycode.identity.UserSummary;
import com.bunnycode.identity.domain.UserAccountRepository;
import com.bunnycode.identity.domain.UserAccountRepository.UserIdRow;
import com.bunnycode.identity.domain.UserSummaryRow;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.storage.FileStorage;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueryService implements UserDirectory {

    private final UserAccountRepository users;
    private final FileStorage fileStorage;

    UserQueryService(UserAccountRepository users, FileStorage fileStorage) {
        this.users = users;
        this.fileStorage = fileStorage;
    }

    public UserProfileView profileOf(long userId) {
        return users.findById(userId)
                .map(user -> UserViews.publicView(user, fileStorage))
                .orElseThrow(() -> BunnyException.notFound("user.not-found", "That user does not exist."));
    }

    public UserProfileView ownProfileOf(long userId) {
        return users.findById(userId)
                .map(user -> UserViews.privateView(user, fileStorage))
                .orElseThrow(() -> BunnyException.notFound("user.not-found", "Your account no longer exists."));
    }

    @Override
    public Optional<UserSummary> findById(long userId) {
        return Optional.ofNullable(findAllByIds(Set.of(userId)).get(userId));
    }

    @Override
    public Map<Long, UserSummary> findAllByIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findSummariesByIdIn(Set.copyOf(userIds)).stream()
                .map(this::toSummary)
                .collect(toUnmodifiableMap(UserSummary::id, Function.identity()));
    }

    @Override
    public Set<Long> findIdsByNameContaining(String fragment, int limit) {
        if (fragment == null || fragment.isBlank()) {
            return Set.of();
        }
        return users.findByNameContainingIgnoreCase(fragment.strip(), Limit.of(limit)).stream()
                .map(UserIdRow::id)
                .collect(toUnmodifiableSet());
    }

    private UserSummary toSummary(UserSummaryRow row) {
        return new UserSummary(row.id(), row.name(), fileStorage.publicUrl(row.pictureKey()));
    }
}
