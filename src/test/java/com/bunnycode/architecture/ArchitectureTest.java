package com.bunnycode.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Layering rules (ArchUnit)")
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bunnycode");

    @Test
    @DisplayName("Domain code depends on no other layer and no web/messaging framework")
    void domainIsIndependent() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..", "..application..", "..infrastructure..",
                        "org.springframework.web..", "org.springframework.messaging..", "jakarta.servlet..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Application services do not depend on controllers or adapters (ports point inwards)")
    void applicationDependsInwards() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Controllers never reach into infrastructure directly")
    void apiDoesNotUseInfrastructure() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Dependencies are injected through constructors, never into fields")
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class).check(CLASSES);
    }

    @Test
    @DisplayName("Only infrastructure talks to Redis, S3, InfluxDB or the STOMP broker")
    void adaptersStayInInfrastructure() {
        noClasses().that().resideOutsideOfPackages("..infrastructure..", "com.bunnycode.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.redis..", "software.amazon.awssdk..", "com.influxdb..")
                .check(CLASSES);
    }
}
