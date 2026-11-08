package com.opspilot.auth.ratelimit;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

public class AuthRateLimitHealthIndicator implements HealthIndicator {
    private final boolean enabled;
    private final StringRedisTemplate redisTemplate;

    public AuthRateLimitHealthIndicator(boolean enabled, StringRedisTemplate redisTemplate) {
        this.enabled = enabled;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        if (!enabled) return Health.up().build();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String response = connection.ping();
            return "PONG".equals(response) ? Health.up().build() : Health.down().build();
        } catch (Exception exception) {
            return Health.down().build();
        }
    }
}
