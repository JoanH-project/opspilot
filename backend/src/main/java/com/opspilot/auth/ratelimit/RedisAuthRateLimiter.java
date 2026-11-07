package com.opspilot.auth.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisAuthRateLimiter implements AuthRateLimiter {
    private static final String LUA_SCRIPT = """
            local ipCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local accountCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            local ipLimit = tonumber(ARGV[1])
            local ipWindow = tonumber(ARGV[2])
            local accountLimit = tonumber(ARGV[3])
            local accountWindow = tonumber(ARGV[4])
            if ipCount >= ipLimit or accountCount >= accountLimit then
              local ipTtl = redis.call('TTL', KEYS[1])
              local accountTtl = redis.call('TTL', KEYS[2])
              local retryAfter = math.max(ipTtl, accountTtl)
              if retryAfter < 1 then
                retryAfter = math.max(ipWindow, accountWindow)
              end
              return retryAfter
            end
            local nextIpCount = redis.call('INCR', KEYS[1])
            if nextIpCount == 1 then redis.call('EXPIRE', KEYS[1], ipWindow) end
            local nextAccountCount = redis.call('INCR', KEYS[2])
            if nextAccountCount == 1 then redis.call('EXPIRE', KEYS[2], accountWindow) end
            return 0
            """;
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthRateLimitProperties properties;
    private final ClientAddressResolver addressResolver;
    private final byte[] hmacKey;
    private final MeterRegistry meterRegistry;

    public RedisAuthRateLimiter(StringRedisTemplate redisTemplate,
                                AuthRateLimitProperties properties,
                                ClientAddressResolver addressResolver,
                                String jwtSecret,
                                MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.addressResolver = addressResolver;
        this.hmacKey = jwtSecret.getBytes(StandardCharsets.UTF_8);
        this.meterRegistry = meterRegistry;
        validateLimit(properties.getIp());
        validateLimit(properties.getAccount());
    }

    @Override
    public void check(AuthAction action, String email, HttpServletRequest request) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String clientAddress = addressResolver.resolve(request);
        String prefix = "opspilot:auth:limit:" + action.name().toLowerCase(Locale.ROOT) + ":";
        String ipKey = prefix + "ip:" + hmac(clientAddress);
        String accountKey = prefix + "account:" + hmac(normalizedEmail);
        AuthRateLimitProperties.Limit ipLimit = properties.getIp();
        AuthRateLimitProperties.Limit accountLimit = properties.getAccount();

        Long retryAfter;
        try {
            retryAfter = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(ipKey, accountKey),
                    Integer.toString(ipLimit.getMaxAttempts()),
                    Long.toString(windowSeconds(ipLimit.getWindow())),
                    Integer.toString(accountLimit.getMaxAttempts()),
                    Long.toString(windowSeconds(accountLimit.getWindow())));
        } catch (DataAccessException exception) {
            recordUnavailable(action);
            throw new AuthenticationProtectionUnavailableException();
        } catch (RuntimeException exception) {
            recordUnavailable(action);
            throw new AuthenticationProtectionUnavailableException();
        }

        if (retryAfter == null) {
            recordUnavailable(action);
            throw new AuthenticationProtectionUnavailableException();
        }
        if (retryAfter > 0) {
            meterRegistry.counter("opspilot.auth.rate_limit.blocked", "action", action.name().toLowerCase(Locale.ROOT))
                    .increment();
            throw new TooManyAuthenticationAttemptsException(retryAfter);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to prepare authentication rate limit key");
        }
    }

    private void recordUnavailable(AuthAction action) {
        meterRegistry.counter("opspilot.auth.rate_limit.unavailable", "action", action.name().toLowerCase(Locale.ROOT))
                .increment();
    }

    private static void validateLimit(AuthRateLimitProperties.Limit limit) {
        if (limit == null || limit.getMaxAttempts() < 1 || limit.getWindow() == null || limit.getWindow().isZero()
                || limit.getWindow().isNegative() || limit.getWindow().getSeconds() < 1) {
            throw new IllegalArgumentException("Authentication rate limit requires a positive count and whole-second window");
        }
    }

    private static long windowSeconds(Duration duration) {
        return duration.getSeconds();
    }
}
