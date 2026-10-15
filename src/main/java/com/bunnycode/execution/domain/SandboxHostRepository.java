package com.bunnycode.execution.domain;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface SandboxHostRepository extends Repository<SandboxHost, String> {

    List<SandboxHost> findAll();
}
