package com.bunnycode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BunnyCodeApplicationIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Given all backing services, when the application starts, then Flyway migrates and Hibernate validates every mapping")
    void startsAgainstRealInfrastructure() {
        assertThat(context.getBeanDefinitionCount()).isPositive();
    }
}
