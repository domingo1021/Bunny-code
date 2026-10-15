package com.bunnycode.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.bunnycode.project.ProjectAccess;
import com.bunnycode.project.ProjectAccess.VersionEditability;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Workspace edit lock")
class EditLockServiceTest {

    private static final Duration TTL = Duration.ofHours(2);
    private final ProjectAccess access = mock(ProjectAccess.class);
    private final EditLockStore store = mock(EditLockStore.class);
    private final EditLockNotifier notifier = mock(EditLockNotifier.class);
    private final EditLockService service = new EditLockService(access, store, notifier, new WorkspaceProperties(TTL));
    private final AuthenticatedUser owner = new AuthenticatedUser(1L, "owner");

    @Nested
    @DisplayName("Given the caller owns an unrecorded version")
    class Owner {

        @Test
        @DisplayName("when the lock is free, then this session edits")
        void acquires() {
            given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.EDITABLE);
            given(store.tryAcquire(10L, "tab-1", TTL)).willReturn(true);

            assertThat(service.acquire(owner, "tab-1", 10L, false).state()).isEqualTo(EditLockState.EDITING);
        }

        @Test
        @DisplayName("when another tab holds the lock, then this one is told and offered a take-over")
        void locked() {
            given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.EDITABLE);
            given(store.tryAcquire(10L, "tab-2", TTL)).willReturn(false);

            assertThat(service.acquire(owner, "tab-2", 10L, false).state()).isEqualTo(EditLockState.LOCKED);
        }

        @Test
        @DisplayName("when taking over, then the previous tab — and only it — is told it was revoked")
        void takeOver() {
            given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.EDITABLE);
            given(store.takeOver(10L, "tab-2", TTL)).willReturn(Optional.of("tab-1"));

            EditLockStatus status = service.acquire(owner, "tab-2", 10L, true);

            assertThat(status.state()).isEqualTo(EditLockState.EDITING);
            then(notifier).should().notifyRevoked(eq(1L), eq("tab-1"),
                    org.mockito.ArgumentMatchers.argThat(revoked -> revoked.state() == EditLockState.REVOKED));
        }

        @Test
        @DisplayName("when re-taking a lock it already holds, then nobody is notified")
        void takeOverSelf() {
            given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.EDITABLE);
            given(store.takeOver(10L, "tab-1", TTL)).willReturn(Optional.of("tab-1"));

            service.acquire(owner, "tab-1", 10L, true);

            then(notifier).should(never()).notifyRevoked(anyLong(), anyString(), any());
        }
    }

    @Test
    @DisplayName("Given someone else's project, then it is read-only and no lock is touched (v1 let anyone edit)")
    void notOwner() {
        given(access.editabilityOf(2L, 10L)).willReturn(VersionEditability.NOT_OWNER);

        EditLockStatus status = service.acquire(new AuthenticatedUser(2L, "visitor"), "tab", 10L, true);

        assertThat(status.state()).isEqualTo(EditLockState.READ_ONLY);
        then(store).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Given a recorded version, then it is replay-only")
    void recorded() {
        given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.ALREADY_RECORDED);
        assertThat(service.acquire(owner, "tab", 10L, false).state()).isEqualTo(EditLockState.RECORDED);
    }

    @Test
    @DisplayName("Given an unknown version, then it is not found")
    void unknown() {
        given(access.editabilityOf(1L, 10L)).willReturn(VersionEditability.NOT_FOUND);
        assertThatThrownBy(() -> service.acquire(owner, "tab", 10L, false)).isInstanceOf(BunnyException.class);
    }
}
