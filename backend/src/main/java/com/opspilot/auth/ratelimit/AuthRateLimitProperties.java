package com.opspilot.auth.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.rate-limit")
public class AuthRateLimitProperties {
    private boolean enabled;
    private Limit ip = new Limit(60, Duration.ofMinutes(1));
    private Limit account = new Limit(20, Duration.ofMinutes(10));
    private String trustedProxies = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Limit getIp() { return ip; }
    public void setIp(Limit ip) { this.ip = ip; }
    public Limit getAccount() { return account; }
    public void setAccount(Limit account) { this.account = account; }
    public String getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(String trustedProxies) { this.trustedProxies = trustedProxies; }

    public static class Limit {
        private int maxAttempts;
        private Duration window;

        public Limit() { }
        public Limit(int maxAttempts, Duration window) {
            this.maxAttempts = maxAttempts;
            this.window = window;
        }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }
}
