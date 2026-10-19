package com.dentacloud;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when one module reaches into another's internals (PRD 8). */
class ModularityTest {
    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(DentaCloudApplication.class).verify();
    }
}
