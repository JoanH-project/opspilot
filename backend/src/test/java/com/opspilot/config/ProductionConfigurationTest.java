package com.opspilot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ProductionConfigurationTest {
    @Test
    void productionProfileFailsFastWhenRequiredValuesAreMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionProfileAcceptsSecureExplicitConfiguration() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        String random = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        new ApplicationContextRunner()
                .withUserConfiguration(ProductionConfiguration.class)
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("prod");
                    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                            "app.jwt.secret=" + random,
                            "app.cors.allowed-origins=https://app.example.com",
                            "spring.datasource.url=jdbc:mysql://db.internal:3306/opspilot?sslMode=VERIFY_IDENTITY",
                            "spring.datasource.username=app",
                            "spring.datasource.password=db-value",
                            "spring.data.redis.host=redis.internal",
                            "spring.data.redis.password=redis-value",
                            "spring.data.redis.ssl.enabled=true",
                            "app.auth.rate-limit.enabled=true",
                            "app.metrics.username=metrics",
                            "app.metrics.password=" + random,
                            "management.server.address=127.0.0.1");
                })
                .run(context -> assertThat(context).hasNotFailed());
    }
}
