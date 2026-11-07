package com.opspilot.config;

import org.springframework.core.env.Environment;

public final class ProductionConfigurationValidator {
    private ProductionConfigurationValidator() { }

    public static void validate(Environment environment) {
        String jwtSecret = required(environment, "app.jwt.secret");
        if (!ProductionJwtSecretPolicy.isStrongRandomSecret(jwtSecret)) {
            throw invalid("JWT_SECRET must be a unique, random URL-safe Base64 secret with at least 32 decoded bytes");
        }

        String corsOrigins = required(environment, "app.cors.allowed-origins");
        try {
            CorsOriginPolicy.validateProductionOrigins(corsOrigins);
        } catch (IllegalArgumentException exception) {
            throw invalid("CORS_ALLOWED_ORIGINS must contain exact HTTPS origins without wildcards");
        }

        String jdbcUrl = required(environment, "spring.datasource.url").toLowerCase();
        if (jdbcUrl.contains("createdatabaseifnotexist=true")
                || jdbcUrl.contains("usessl=false")
                || jdbcUrl.contains("allowpublickeyretrieval=true")
                || !jdbcUrl.contains("sslmode=verify_identity")) {
            throw invalid("Production JDBC must disable database auto-creation and use sslMode=VERIFY_IDENTITY");
        }

        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        required(environment, "spring.data.redis.host");
        required(environment, "spring.data.redis.password");
        if (!environment.getProperty("spring.data.redis.ssl.enabled", Boolean.class, false)) {
            throw invalid("Production Redis TLS must be enabled");
        }
        if (!environment.getProperty("app.auth.rate-limit.enabled", Boolean.class, false)) {
            throw invalid("Production authentication rate limiting must be enabled");
        }

        required(environment, "app.metrics.username");
        String metricsPassword = required(environment, "app.metrics.password");
        if (!ProductionJwtSecretPolicy.isStrongRandomSecret(metricsPassword)) {
            throw invalid("METRICS_PASSWORD must be a unique, random URL-safe Base64 secret with at least 32 decoded bytes");
        }
        required(environment, "management.server.address");
    }

    private static String required(Environment environment, String property) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException exception) {
            throw invalid("Missing required production configuration: " + property);
        }
        if (value == null || value.isBlank()) {
            throw invalid("Missing required production configuration: " + property);
        }
        return value;
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid production configuration: " + message);
    }
}
