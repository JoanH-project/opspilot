package com.opspilot.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger logger = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String suppliedRequestId = request.getHeader(HEADER_NAME);
        String requestId = suppliedRequestId != null && VALID_REQUEST_ID.matcher(suppliedRequestId).matches()
                ? suppliedRequestId
                : UUID.randomUUID().toString();
        String previousRequestId = MDC.get("requestId");
        long startedAt = System.nanoTime();

        response.setHeader(HEADER_NAME, requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            Object mappedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = mappedPattern instanceof String pattern ? pattern : "unmapped";
            if (response.getStatus() >= 500) {
                logger.warn("http_request method={} route={} status={} duration_ms={}",
                        request.getMethod(), route, response.getStatus(), durationMillis);
            } else {
                logger.info("http_request method={} route={} status={} duration_ms={}",
                        request.getMethod(), route, response.getStatus(), durationMillis);
            }

            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }
}
