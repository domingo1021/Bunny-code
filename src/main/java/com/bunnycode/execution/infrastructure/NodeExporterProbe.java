package com.bunnycode.execution.infrastructure;

import com.bunnycode.execution.application.ExecutionProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reads a host's load from its node-exporter. Any failure means "unhealthy", never an exception. */
@Component
class NodeExporterProbe implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NodeExporterProbe.class);
    private static final Duration CPU_SAMPLE_INTERVAL = Duration.ofMillis(250);

    private final ExecutionProperties.HostHealth settings;
    private final HttpClient http;

    NodeExporterProbe(ExecutionProperties properties) {
        this.settings = properties.hostHealth();
        this.http = HttpClient.newBuilder().connectTimeout(settings.probeTimeout()).build();
    }

    Optional<HostLoad> probe(String host) {
        URI metrics = URI.create("http://" + host + ":" + settings.metricsPort() + "/metrics");
        try {
            String first = fetch(metrics);
            Thread.sleep(CPU_SAMPLE_INTERVAL);
            String second = fetch(metrics);
            return Optional.of(new HostLoad(host,
                    NodeExporterMetrics.cpuUtilisation(first, second),
                    NodeExporterMetrics.memoryUtilisation(second)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.warn("Sandbox host {} is unhealthy: {}", host, e.toString());
            return Optional.empty();
        }
    }

    private String fetch(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.probeTimeout()).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri);
        }
        return response.body();
    }

    @Override
    public void close() {
        http.close();
    }
}
