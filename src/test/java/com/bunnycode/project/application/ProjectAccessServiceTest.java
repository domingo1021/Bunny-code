package com.bunnycode.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bunnycode.project.ProjectAccess.VersionEditability;
import com.bunnycode.project.domain.ProjectRows.VersionAccessRow;
import com.bunnycode.project.domain.ProjectVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAccess — who may edit a version")
class ProjectAccessServiceTest {

    @Mock
    private ProjectVersionRepository versions;
    @InjectMocks
    private ProjectAccessService access;

    @Test
    @DisplayName("The owner may edit an unrecorded version")
    void ownerMayEdit() {
        given(versions.findAccess(10L)).willReturn(Optional.of(new VersionAccessRow(10L, 1L, false)));
        assertThat(access.editabilityOf(1L, 10L)).isEqualTo(VersionEditability.EDITABLE);
    }

    @Test
    @DisplayName("Anyone else may not — the v1 COUNT(*) bug let every user through")
    void othersMayNot() {
        given(versions.findAccess(10L)).willReturn(Optional.of(new VersionAccessRow(10L, 1L, false)));
        assertThat(access.editabilityOf(2L, 10L)).isEqualTo(VersionEditability.NOT_OWNER);
    }

    @Test
    @DisplayName("A recorded version is replay-only, even for its owner")
    void recordedIsReplayOnly() {
        given(versions.findAccess(10L)).willReturn(Optional.of(new VersionAccessRow(10L, 1L, true)));
        assertThat(access.editabilityOf(1L, 10L)).isEqualTo(VersionEditability.ALREADY_RECORDED);
    }

    @Test
    @DisplayName("An unknown version is reported as not found")
    void unknown() {
        given(versions.findAccess(10L)).willReturn(Optional.empty());
        assertThat(access.editabilityOf(1L, 10L)).isEqualTo(VersionEditability.NOT_FOUND);
    }
}
