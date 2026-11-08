package com.opspilot.auth.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfiguration {
    @Bean
    ClientAddressResolver clientAddressResolver(AuthRateLimitProperties properties) {
        return new ClientAddressResolver(properties.getTrustedProxies());
    }

    @Bean
    AuthRateLimitHealthIndicator authRateLimitHealthIndicator(
            @Value("${app.auth.rate-limit.enabled:false}") boolean enabled, StringRedisTemplate redisTemplate) {
        return new AuthRateLimitHealthIndicator(enabled, redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.rate-limit", name = "enabled", havingValue = "true")
    AuthRateLimiter redisAuthRateLimiter(StringRedisTemplate redisTemplate,
                                         AuthRateLimitProperties properties,
                                         ClientAddressResolver addressResolver,
                                         @Value("${app.jwt.secret}") String jwtSecret,
                                         MeterRegistry meterRegistry) {
        return new RedisAuthRateLimiter(redisTemplate, properties, addressResolver, jwtSecret, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.auth.rate-limit", name = "enabled", havingValue = "false", matchIfMissing = true)
    AuthRateLimiter noopAuthRateLimiter() {
        return (action, email, request) -> { };
    }
}
