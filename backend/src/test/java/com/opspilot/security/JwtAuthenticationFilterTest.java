package com.opspilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.auth.JwtService;
import com.opspilot.user.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {
    @Test
    void databaseFailureWhileResolvingValidTokenReturnsSanitized503() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn(42L);
        when(userRepository.findById(42L)).thenThrow(
                new DataAccessResourceFailureException("sensitive database connection detail"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository,
                new AuthenticationErrorWriter(new ObjectMapper().findAndRegisterModules()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new AssertionError("Request must stop when the authentication database lookup fails");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("A required service is temporarily unavailable")
                .doesNotContain("sensitive database connection detail");
    }

    @Test
    void databaseFailureWhileOpeningTransactionReturnsSanitized503() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractUserId("valid-token")).thenReturn(42L);
        when(userRepository.findById(42L)).thenThrow(
                new CannotCreateTransactionException("sensitive database connection detail"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository,
                new AuthenticationErrorWriter(new ObjectMapper().findAndRegisterModules()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw new AssertionError("Request must stop when the authentication transaction cannot start");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("A required service is temporarily unavailable")
                .doesNotContain("sensitive database connection detail");
    }
}
