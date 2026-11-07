package com.opspilot.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

public final class CorsOriginPolicy {
    private CorsOriginPolicy() { }

    public static List<String> parse(String configuredOrigins) {
        if (configuredOrigins == null || configuredOrigins.isBlank()) {
            throw new IllegalArgumentException("At least one exact CORS origin must be configured");
        }

        List<String> origins = Arrays.stream(configuredOrigins.split(",", -1))
                .map(String::trim)
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("CORS origins cannot contain empty entries");
        }

        for (String origin : origins) {
            validateExactOrigin(origin);
        }
        return origins;
    }

    public static void validateProductionOrigins(String configuredOrigins) {
        for (String origin : parse(configuredOrigins)) {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || "localhost".equalsIgnoreCase(host)
                    || host.endsWith(".localhost")) {
                throw new IllegalArgumentException("Production CORS origins must be exact HTTPS origins");
            }
        }
    }

    private static void validateExactOrigin(String origin) {
        if (origin.equalsIgnoreCase("null") || origin.contains("*")) {
            throw new IllegalArgumentException("CORS wildcard and null origins are not allowed");
        }

        try {
            URI uri = URI.create(origin);
            if (!uri.isAbsolute()
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("CORS entries must be exact scheme/host/port origins");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid CORS origin entry", exception);
        }
    }
}
