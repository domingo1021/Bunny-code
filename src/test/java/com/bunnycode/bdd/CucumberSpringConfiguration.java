package com.bunnycode.bdd;

import com.bunnycode.TestcontainersConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// Registered explicitly (not @Component) so the application's component scan never picks up
// test-only, scenario-scoped beans in other test contexts.
@Import({TestcontainersConfiguration.class, World.class, ApiClient.class})
public class CucumberSpringConfiguration {
}
