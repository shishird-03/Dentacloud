package com.dentacloud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps docs/openapi.yaml (the API contract the Android client is generated from) in step with the
 * code. Fails on drift; rerun with UPDATE_OPENAPI=1 to accept the change, then rebuild the app.
 */
class OpenApiExportTest extends IntegrationTest {
    private static final Path SPEC = Path.of("..", "docs", "openapi.yaml");

    @Test
    void specIsUpToDate() throws IOException {
        String live = http.getForObject("/v3/api-docs.yaml", String.class).replace("\r\n", "\n");
        if ("1".equals(System.getenv("UPDATE_OPENAPI")) || !Files.exists(SPEC)) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, live, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(SPEC, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("docs/openapi.yaml is stale; run with UPDATE_OPENAPI=1")
                .isEqualTo(live);
    }
}
