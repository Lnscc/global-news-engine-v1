package com.example.globalnewsenginev1;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GlobalNewsEngineV1ApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void applicationModulesAreValid() {
        ApplicationModules.of(GlobalNewsEngineV1Application.class).verify();
    }
}
