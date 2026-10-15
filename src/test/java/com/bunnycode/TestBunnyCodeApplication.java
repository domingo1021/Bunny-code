package com.bunnycode;

import org.springframework.boot.SpringApplication;

/**
 * Runs the API locally with every dependency in Testcontainers:
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=local}.
 */
public class TestBunnyCodeApplication {

    public static void main(String[] args) {
        SpringApplication.from(BunnyCodeApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
