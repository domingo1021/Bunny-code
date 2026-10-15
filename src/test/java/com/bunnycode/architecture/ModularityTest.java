package com.bunnycode.architecture;

import com.bunnycode.BunnyCodeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@DisplayName("Module boundaries (Spring Modulith)")
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(BunnyCodeApplication.class);

    @Test
    @DisplayName("Modules only use each other's public API and have no dependency cycles")
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    @DisplayName("Module documentation is generated (target/spring-modulith-docs)")
    void writesDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
