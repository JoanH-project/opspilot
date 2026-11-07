package com.opspilot.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.common.error.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationErrorWriter {
    private final ObjectMapper objectMapper;
    public AuthenticationErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletResponse response, String message) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, message);
    }

    public void writeUnavailable(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.SERVICE_UNAVAILABLE,
                "A required service is temporarily unavailable");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(),
                status.value(), status.getReasonPhrase(), message, Map.of()));
    }
}
