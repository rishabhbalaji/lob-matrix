package com.lobmatrix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LobMatrixApplicationTest {

    @Test
    @DisplayName("Verify Spring Boot application context boots cleanly with all Java 21 dependencies")
    void contextLoads() {
        String javaVersion = System.getProperty("java.version");
        assertThat(javaVersion).isNotNull();
    }
}
