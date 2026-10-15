package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Node exporter metrics")
class NodeExporterMetricsTest {

    private static String cpuSample(double idle0, double user0, double idle1, double user1) {
        return """
                # HELP node_cpu_seconds_total Seconds the CPUs spent in each mode.
                # TYPE node_cpu_seconds_total counter
                node_cpu_seconds_total{cpu="0",mode="idle"} %s
                node_cpu_seconds_total{cpu="0",mode="user"} %s
                node_cpu_seconds_total{cpu="1",mode="idle"} %s
                node_cpu_seconds_total{cpu="1",mode="user"} %s
                node_load1 0.5
                """.formatted(idle0, user0, idle1, user1);
    }

    @Test
    @DisplayName("Given two samples, then CPU utilisation is 1 - Δidle/Δtotal across all cores")
    void cpuUtilisation() {
        String before = cpuSample(100, 100, 100, 100);
        String after = cpuSample(103, 101, 101, 103); // Δidle = 4, Δtotal = 8

        assertThat(NodeExporterMetrics.cpuUtilisation(before, after)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("Given scientific notation, then values are still parsed")
    void scientificNotation() {
        String before = cpuSample(1.0e2, 1.0e2, 1.0e2, 1.0e2);
        String after = cpuSample(1.02e2, 1.0e2, 1.02e2, 1.0e2);

        assertThat(NodeExporterMetrics.cpuUtilisation(before, after)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("Given counters that did not move, then the sample is rejected rather than reported as idle")
    void stuckCounters() {
        String sample = cpuSample(100, 100, 100, 100);
        assertThatThrownBy(() -> NodeExporterMetrics.cpuUtilisation(sample, sample))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Memory utilisation is 1 - MemAvailable/MemTotal")
    void memoryUtilisation() {
        String sample = """
                node_memory_MemAvailable_bytes 2.5e+09
                node_memory_MemTotal_bytes 1e+10
                node_memory_MemAvailable_bytes_extra 1
                """;
        assertThat(NodeExporterMetrics.memoryUtilisation(sample)).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("Missing metrics are an error (the host is then treated as unhealthy)")
    void missingMetrics() {
        assertThatThrownBy(() -> NodeExporterMetrics.memoryUtilisation("nothing here"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NodeExporterMetrics.cpuTimes("nothing here"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
