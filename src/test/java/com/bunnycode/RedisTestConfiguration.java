package com.bunnycode;

import com.bunnycode.support.TestContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Redis only — for {@code @DataRedisTest} slices. */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestConfiguration {

    @Bean
    DynamicPropertyRegistrar redisOnlyProperties() {
        return registrar();
    }

    static DynamicPropertyRegistrar registrar() {
        return registry -> {
            registry.add("spring.data.redis.host", () -> TestContainers.redis().getHost());
            registry.add("spring.data.redis.port", () -> TestContainers.redis().getMappedPort(6379));
            registry.add("spring.data.redis.username", () -> "");
            registry.add("spring.data.redis.password", () -> "");
        };
    }
}
