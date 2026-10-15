package com.bunnycode.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.bunnycode.project.domain.CodingEvent;
import com.bunnycode.project.domain.ProjectRepository;
import com.bunnycode.project.domain.ProjectVersionRepository;
import com.bunnycode.project.domain.RecordingRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.storage.StorageLayout;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("RecordingService input rules")
class RecordingServiceTest {

    private final CodingEventStore eventStore = mock(CodingEventStore.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final RecordingService service = new RecordingService(mock(ProjectRepository.class),
            mock(ProjectVersionRepository.class), mock(RecordingRepository.class), eventStore, storage,
            mock(StorageLayout.class), new RecordingProperties(new RecordingProperties.Influx("u", "t", "o", "b"), 3),
            Clock.systemUTC(), mock(PlatformTransactionManager.class));
    private final AuthenticatedUser owner = new AuthenticatedUser(1L, "owner");

    @Test
    @DisplayName("When there are no events, then nothing is saved and the user is told why")
    void rejectsEmptySessions() {
        assertThatThrownBy(() -> service.save(owner, "bunny", 1, List.of(), "code"))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("recording.empty"));
        thenNothingWasWritten();
    }

    @Test
    @DisplayName("When there are more events than allowed, then nothing is saved")
    void rejectsOversizedSessions() {
        List<CodingEvent> events = Collections.nCopies(4, new CodingEvent("create", 0, 0, "a", Instant.EPOCH));

        assertThatThrownBy(() -> service.save(owner, "bunny", 1, events, "code"))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("recording.too-long"));
        thenNothingWasWritten();
    }

    private void thenNothingWasWritten() {
        then(eventStore).should(org.mockito.Mockito.never()).append(anyLong(), anyLong(), anyLong(), any());
        then(storage).should(org.mockito.Mockito.never()).store(any(), any(), any());
    }
}
