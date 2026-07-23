package com.eems;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class EemsApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context (security config, JPA entities,
        // controllers) wires up correctly against the in-memory H2 DB.
    }
}
