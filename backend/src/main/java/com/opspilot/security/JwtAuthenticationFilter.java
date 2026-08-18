package com.opspilot.security;

import java.io.IOException;

import com.opspilot.auth.JwtService;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthenticationErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   AuthenticationErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.validateToken(token)) {
            errorWriter.write(response, "Invalid or expired access token");
            return;
        }

        try {
            Long userId = jwtService.extractUserId(token);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                errorWriter.write(response, "Invalid or expired access token");
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, java.util.List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(response, "Invalid or expired access token");
        }
    }
}
