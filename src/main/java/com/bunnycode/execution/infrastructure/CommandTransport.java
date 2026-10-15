package com.bunnycode.execution.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Where a sandbox command runs: on this machine, or on a sandbox host over SSH. */
sealed interface CommandTransport {

    List<String> wrap(List<String> command);

    String describe();

    record Local() implements CommandTransport {

        @Override
        public List<String> wrap(List<String> command) {
            return List.copyOf(command);
        }

        @Override
        public String describe() {
            return "local";
        }
    }

    /**
     * SSH joins the remote arguments with spaces and hands them to the remote shell, so every
     * token must be shell-inert. That holds because all tokens are produced by
     * {@link DockerCommands} from validated configuration and generated container names — no
     * user input ever reaches this command line (user code travels over stdin).
     */
    record Ssh(String user, String host, String identityFile) implements CommandTransport {

        private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
        private static final Pattern SAFE_TOKEN = Pattern.compile("^[\\w./:{}=-]+$");

        public Ssh {
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(identityFile, "identityFile");
            if (host == null || !HOST.matcher(host).matches()) {
                throw new IllegalArgumentException("Invalid sandbox host: " + host);
            }
        }

        @Override
        public List<String> wrap(List<String> command) {
            for (String token : command) {
                if (!SAFE_TOKEN.matcher(token).matches()) {
                    throw new IllegalArgumentException("Refusing to send a shell-unsafe token over SSH: " + token);
                }
            }
            var wrapped = new ArrayList<>(List.of(
                    "ssh", "-i", identityFile,
                    "-o", "BatchMode=yes",
                    "-o", "ConnectTimeout=5",
                    "-o", "StrictHostKeyChecking=accept-new",
                    user + "@" + host, "--"));
            wrapped.addAll(command);
            return List.copyOf(wrapped);
        }

        @Override
        public String describe() {
            return "ssh:" + host;
        }
    }
}
