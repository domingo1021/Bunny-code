package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bunnycode.execution.application.ExecutionProperties.Mode;
import com.bunnycode.execution.domain.SandboxHost;
import com.bunnycode.execution.domain.SandboxHostRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sandbox host selection")
class SandboxTargetSelectorTest {

    private final SandboxHostRepository hosts = mock(SandboxHostRepository.class);
    private final NodeExporterProbe probe = mock(NodeExporterProbe.class);

    private SandboxTargetSelector selector(Mode mode) {
        return new SandboxTargetSelector(DockerCommandsTest.properties(mode, Duration.ofSeconds(10)), hosts, probe);
    }

    @Test
    @DisplayName("In local mode, jobs run on this machine without probing anything")
    void local() {
        assertThat(selector(Mode.LOCAL).select()).isInstanceOf(CommandTransport.Local.class);
    }

    @Test
    @DisplayName("In SSH mode, the least-loaded healthy host wins")
    void leastLoaded() {
        given(hosts.findAll()).willReturn(List.of(new SandboxHost("10.0.0.1"), new SandboxHost("10.0.0.2"),
                new SandboxHost("10.0.0.3")));
        given(probe.probe("10.0.0.1")).willReturn(Optional.of(new HostLoad("10.0.0.1", 0.5, 0.4)));
        given(probe.probe("10.0.0.2")).willReturn(Optional.of(new HostLoad("10.0.0.2", 0.1, 0.2)));
        given(probe.probe("10.0.0.3")).willReturn(Optional.of(new HostLoad("10.0.0.3", 0.95, 0.1)));

        assertThat(selector(Mode.SSH).select())
                .isEqualTo(new CommandTransport.Ssh("ubuntu", "10.0.0.2", "/keys/k"));
    }

    @Test
    @DisplayName("When every host is overloaded or unreachable, the user is told Bunny is busy")
    void allBusy() {
        given(hosts.findAll()).willReturn(List.of(new SandboxHost("10.0.0.1"), new SandboxHost("10.0.0.2")));
        given(probe.probe("10.0.0.1")).willReturn(Optional.of(new HostLoad("10.0.0.1", 0.9, 0.1)));
        given(probe.probe("10.0.0.2")).willReturn(Optional.empty());

        assertThatThrownBy(() -> selector(Mode.SSH).select())
                .isInstanceOfSatisfying(BunnyException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.UNAVAILABLE);
                    assertThat(e.code()).isEqualTo("execution.busy");
                });
    }

    @Test
    @DisplayName("With no hosts configured, jobs are refused the same way")
    void noHosts() {
        given(hosts.findAll()).willReturn(List.of());
        assertThatThrownBy(() -> selector(Mode.SSH).select()).isInstanceOf(BunnyException.class);
    }
}
