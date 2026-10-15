package com.bunnycode;

import com.bunnycode.support.TestContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the full application at MySQL, Redis, InfluxDB and S3 (LocalStack) running in
 * Testcontainers. Used by integration tests, the BDD suite and {@link TestBunnyCodeApplication}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    DynamicPropertyRegistrar mysqlProperties() {
        return MySqlTestConfiguration.registrar();
    }

    @Bean
    DynamicPropertyRegistrar redisProperties() {
        return RedisTestConfiguration.registrar();
    }

    @Bean
    DynamicPropertyRegistrar influxProperties() {
        return registry -> {
            registry.add("bunny.recording.influx.url", () -> TestContainers.influx().getUrl());
            registry.add("bunny.recording.influx.token", () -> TestContainers.INFLUX_TOKEN);
            registry.add("bunny.recording.influx.org", () -> TestContainers.INFLUX_ORG);
            registry.add("bunny.recording.influx.bucket", () -> TestContainers.INFLUX_BUCKET);
        };
    }

    @Bean
    DynamicPropertyRegistrar storageProperties() {
        return registry -> {
            registry.add("bunny.storage.endpoint", () -> TestContainers.localStack().getEndpoint().toString());
            registry.add("bunny.storage.region", () -> TestContainers.localStack().getRegion());
            registry.add("bunny.storage.access-key", () -> TestContainers.localStack().getAccessKey());
            registry.add("bunny.storage.secret-key", () -> TestContainers.localStack().getSecretKey());
            registry.add("bunny.storage.bucket", () -> TestContainers.S3_BUCKET);
            registry.add("bunny.storage.public-base-url", TestContainers::publicBaseUrl);
        };
    }
}
