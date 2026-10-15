package com.bunnycode.execution.infrastructure;

import com.bunnycode.execution.application.ExecutionProperties;
import com.bunnycode.execution.domain.SandboxHost;
import com.bunnycode.execution.domain.SandboxHostRepository;
import com.bunnycode.shared.error.BunnyException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Component;

/**
 * Picks where the next job runs. In SSH mode all hosts are probed in parallel and the least
 * loaded healthy one wins (v1 took whichever answered first, even if busier).
 */
@Component
class SandboxTargetSelector {

    private static final CommandTransport LOCAL = new CommandTransport.Local();

    private final ExecutionProperties properties;
    private final SandboxHostRepository hosts;
    private final NodeExporterProbe probe;

    SandboxTargetSelector(ExecutionProperties properties, SandboxHostRepository hosts, NodeExporterProbe probe) {
        this.properties = properties;
        this.hosts = hosts;
        this.probe = probe;
    }

    CommandTransport select() {
        if (properties.mode() == ExecutionProperties.Mode.LOCAL) {
            return LOCAL;
        }
        ExecutionProperties.HostHealth limits = properties.hostHealth();
        return probeAll(hosts.findAll().stream().map(SandboxHost::getAddress).toList()).stream()
                .filter(load -> load.isBelow(limits.cpuThreshold(), limits.memoryThreshold()))
                .min(Comparator.comparingDouble(HostLoad::peak))
                .<CommandTransport>map(load -> new CommandTransport.Ssh(
                        properties.ssh().user(), load.host(), properties.ssh().identityFile()))
                .orElseThrow(() -> BunnyException.unavailable("execution.busy",
                        "Bunny is too busy to run code right now — try again in a moment."));
    }

    private List<HostLoad> probeAll(List<String> addresses) {
        try (ExecutorService probes = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<HostLoad>>> results = addresses.stream()
                    .map(address -> probes.submit(() -> probe.probe(address)))
                    .toList();
            return results.stream().map(SandboxTargetSelector::await).flatMap(Optional::stream).toList();
        }
    }

    private static Optional<HostLoad> await(Future<Optional<HostLoad>> result) {
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }
}
