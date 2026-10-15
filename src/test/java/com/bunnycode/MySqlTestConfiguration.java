package com.bunnycode;

import com.bunnycode.support.TestContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** MySQL only — for {@code @DataJpaTest} slices, which need nothing else. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {

    @Bean
    DynamicPropertyRegistrar mysqlOnlyProperties() {
        return registrar();
    }

    static DynamicPropertyRegistrar registrar() {
        return registry -> {
            registry.add("spring.datasource.url", () -> TestContainers.mysql().getJdbcUrl());
            registry.add("spring.datasource.username", () -> TestContainers.mysql().getUsername());
            registry.add("spring.datasource.password", () -> TestContainers.mysql().getPassword());
        };
    }
}
