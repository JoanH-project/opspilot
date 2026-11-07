package com.opspilot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ProductionJwtSecretPolicyTest {
    @Test
    void acceptsRandomUrlSafeSecretAndRejectsDevelopmentOrLowEntropyValues() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertTrue(ProductionJwtSecretPolicy.isStrongRandomSecret(secret));
        assertFalse(ProductionJwtSecretPolicy.isStrongRandomSecret(ProductionJwtSecretPolicy.DEVELOPMENT_SECRET));
        assertFalse(ProductionJwtSecretPolicy.isStrongRandomSecret("A".repeat(64)));
    }
}
