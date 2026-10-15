package com.bunnycode.execution.infrastructure;

/** Utilisation of one sandbox host, each in {@code [0, 1]}. */
record HostLoad(String host, double cpu, double memory) {

    boolean isBelow(double cpuThreshold, double memoryThreshold) {
        return cpu < cpuThreshold && memory < memoryThreshold;
    }

    /** The scarcer resource decides how loaded a host is. */
    double peak() {
        return Math.max(cpu, memory);
    }
}
