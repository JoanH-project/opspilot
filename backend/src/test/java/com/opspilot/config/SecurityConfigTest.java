package com.opspilot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {
    @Test
    void corsAllowsConfiguredViteOriginAndRequiredApiHeaders() {
        CorsConfiguration configuration = configuration("http://localhost:5173");

        assertEquals("http://localhost:5173",
                configuration.checkOrigin("http://localhost:5173"));
        assertEquals(List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.OPTIONS),
                configuration.checkHttpMethod(HttpMethod.PATCH));
        assertEquals(List.of("Authorization", "Content-Type"),
                configuration.checkHeaders(List.of("Authorization", "Content-Type")));
        assertFalse(configuration.getAllowCredentials());
    }

    @Test
    void corsRejectsOriginsThatAreNotConfigured() {
        CorsConfiguration configuration = configuration(
                "http://localhost:5173, https://app.example.com");

        assertEquals("https://app.example.com",
                configuration.checkOrigin("https://app.example.com"));
        assertNull(configuration.checkOrigin("https://untrusted.example.com"));
    }

    private CorsConfiguration configuration(String allowedOrigins) {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource(allowedOrigins);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/workspaces");
        return source.getCorsConfiguration(request);
    }
}
