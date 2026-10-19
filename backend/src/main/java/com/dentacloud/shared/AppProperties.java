package com.dentacloud.shared;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Secrets come from the environment (.env.example); the dev defaults in application.yml are dummies. */
@ConfigurationProperties("dentacloud")
public record AppProperties(String jwtSecret, Duration accessTtl, Duration refreshTtl,
                            String fieldEncKey, String blindIndexKey) {
    public AppProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("dentacloud.jwt-secret must be at least 32 characters");
        }
    }
}
