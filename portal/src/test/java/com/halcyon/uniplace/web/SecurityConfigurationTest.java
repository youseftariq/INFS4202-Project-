package com.halcyon.uniplace.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * TC-SEC-004 / SEC-03: error pages never show stack traces or class names.
 * Plain unit test (no Spring): it reads the application's own properties file, because the test
 * profile has its own application.properties that hides the real one. Maven runs it from the portal folder.
 */
class SecurityConfigurationTest {

    private static Properties mainProperties() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            p.load(in);
        }
        return p;
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-004 / SEC-03: the stack trace is not included in error pages (DEFECT)")
    void stackTraceIsNotIncluded() throws IOException {
        assertNotEquals("always", mainProperties().getProperty("server.error.include-stacktrace"),
                "server.error.include-stacktrace must be never");
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-004 / SEC-03: the exception class name is not included in error pages (DEFECT)")
    void exceptionClassIsNotIncluded() throws IOException {
        assertNotEquals("true", mainProperties().getProperty("server.error.include-exception"),
                "server.error.include-exception must be false");
    }
}
