package com.bunnycode.execution.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Runs an external command given as an argument vector — never through a shell — feeding
 * {@code stdin} and capturing bounded stdout/stderr. Streams are drained concurrently on virtual
 * threads so a chatty process can never block on a full pipe.
 */
@Component
class ProcessRunner {

    ProcessResult run(List<String> command, byte[] stdin, Duration timeout, int maxOutputBytes) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start " + command.getFirst(), e);
        }
        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Captured> stdout = io.submit(() -> capture(process.getInputStream(), maxOutputBytes));
            Future<Captured> stderr = io.submit(() -> capture(process.getErrorStream(), maxOutputBytes));
            io.submit(() -> feed(process.getOutputStream(), stdin));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            Captured out = stdout.get();
            Captured err = stderr.get();
            return new ProcessResult(finished ? process.exitValue() : -1, out.text(), err.text(),
                    !finished, out.truncated() || err.truncated());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + command.getFirst(), e);
        } catch (ExecutionException e) {
            process.destroyForcibly();
            throw new IllegalStateException("Failed to capture output of " + command.getFirst(), e.getCause());
        }
    }

    private static Void feed(OutputStream stdin, byte[] input) {
        try (stdin) {
            stdin.write(input);
        } catch (IOException e) {
            // The process exited before reading everything (e.g. it failed to start); its exit code tells the story.
        }
        return null;
    }

    /** Keeps the first {@code limit} bytes and discards (but still drains) the rest. */
    private static Captured capture(InputStream stream, int limit) throws IOException {
        var kept = new ByteArrayOutputStream();
        boolean truncated = false;
        byte[] buffer = new byte[8192];
        try (stream) {
            for (int read = stream.read(buffer); read != -1; read = stream.read(buffer)) {
                int room = limit - kept.size();
                if (room > 0) {
                    kept.write(buffer, 0, Math.min(room, read));
                }
                truncated |= read > room;
            }
        }
        return new Captured(kept.toString(StandardCharsets.UTF_8), truncated);
    }

    private record Captured(String text, boolean truncated) {
    }

    /** {@code exitCode} is -1 when the process was killed for exceeding the timeout. */
    record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut, boolean truncated) {
    }
}
