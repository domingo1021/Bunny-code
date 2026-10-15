package com.bunnycode.execution.infrastructure;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the two node-exporter samples the host check needs. Pure functions over the Prometheus
 * text format, so they are unit-tested without a network.
 */
final class NodeExporterMetrics {

    private static final Pattern CPU_LINE =
            Pattern.compile("^node_cpu_seconds_total\\{([^}]*)}\\s+(\\S+)$");
    private static final Pattern MODE_LABEL = Pattern.compile("(?:^|,)mode=\"([^\"]+)\"");

    private NodeExporterMetrics() {
    }

    /**
     * CPU utilisation in {@code [0, 1]} across all cores between two samples, i.e.
     * {@code 1 - Δidle / Δtotal}.
     */
    static double cpuUtilisation(String earlier, String later) {
        CpuTimes before = cpuTimes(earlier);
        CpuTimes after = cpuTimes(later);
        double totalDelta = after.total() - before.total();
        if (totalDelta <= 0) {
            throw new IllegalArgumentException("CPU counters did not advance between samples");
        }
        double idleDelta = after.idle() - before.idle();
        return clamp(1 - idleDelta / totalDelta);
    }

    /** Memory utilisation in {@code [0, 1]}: {@code 1 - MemAvailable / MemTotal}. */
    static double memoryUtilisation(String sample) {
        double available = gauge(sample, "node_memory_MemAvailable_bytes");
        double total = gauge(sample, "node_memory_MemTotal_bytes");
        if (total <= 0) {
            throw new IllegalArgumentException("node_memory_MemTotal_bytes must be positive");
        }
        return clamp(1 - available / total);
    }

    static CpuTimes cpuTimes(String sample) {
        double idle = 0;
        double total = 0;
        for (String line : sample.lines().toList()) {
            Matcher cpu = CPU_LINE.matcher(line.strip());
            if (!cpu.matches()) {
                continue;
            }
            double seconds = Double.parseDouble(cpu.group(2));
            total += seconds;
            Matcher mode = MODE_LABEL.matcher(cpu.group(1));
            if (mode.find() && "idle".equals(mode.group(1))) {
                idle += seconds;
            }
        }
        if (total == 0) {
            throw new IllegalArgumentException("No node_cpu_seconds_total samples found");
        }
        return new CpuTimes(idle, total);
    }

    private static double gauge(String sample, String name) {
        return sample.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(name + " ") || line.startsWith(name + "\t"))
                .map(line -> line.substring(name.length()).strip())
                .mapToDouble(Double::parseDouble)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Metric not found: " + name));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    record CpuTimes(double idle, double total) {
    }
}
