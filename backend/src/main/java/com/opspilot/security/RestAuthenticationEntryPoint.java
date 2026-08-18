package com.opspilot.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthenticationErrorWriter errorWriter;
    public RestAuthenticationEntryPoint(AuthenticationErrorWriter errorWriter) { this.errorWriter = errorWriter; }
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        errorWriter.write(response, "Authentication required");
    }
}
