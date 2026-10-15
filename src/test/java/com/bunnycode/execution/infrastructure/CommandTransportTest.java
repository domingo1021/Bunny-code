package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Command transports")
class CommandTransportTest {

    @Test
    @DisplayName("Local runs the command unchanged")
    void local() {
        assertThat(new CommandTransport.Local().wrap(List.of("docker", "ps"))).containsExactly("docker", "ps");
    }

    @Test
    @DisplayName("SSH prefixes a non-interactive ssh call and ends option parsing with --")
    void ssh() {
        var ssh = new CommandTransport.Ssh("ubuntu", "10.0.0.5", "/keys/sandbox.pem");

        assertThat(ssh.wrap(List.of("docker", "rm", "--force", "bunny-1")))
                .startsWith("ssh", "-i", "/keys/sandbox.pem")
                .contains("BatchMode=yes")
                .endsWith("ubuntu@10.0.0.5", "--", "docker", "rm", "--force", "bunny-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.5; rm -rf /", "$(whoami)", "-oProxyCommand=evil", "", "host name"})
    @DisplayName("SSH refuses host values that could inject options or commands")
    void rejectsHostileHosts(String host) {
        assertThatThrownBy(() -> new CommandTransport.Ssh("ubuntu", host, "/keys/k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a;b", "$(id)", "`id`", "a b", "a|b", "a&b", "a>b", "'quoted'"})
    @DisplayName("SSH refuses to forward shell-active tokens to the remote shell")
    void rejectsShellTokens(String token) {
        var ssh = new CommandTransport.Ssh("ubuntu", "10.0.0.5", "/keys/k");
        assertThatThrownBy(() -> ssh.wrap(List.of("docker", token))).isInstanceOf(IllegalArgumentException.class);
    }
}
