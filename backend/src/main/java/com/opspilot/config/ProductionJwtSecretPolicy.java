package com.opspilot.config;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public final class ProductionJwtSecretPolicy {
    public static final String DEVELOPMENT_SECRET = "opspilot-development-only-jwt-secret-change-me-2026";

    private ProductionJwtSecretPolicy() { }

    public static boolean isStrongRandomSecret(String secret) {
        if (secret == null
                || secret.length() < 43
                || DEVELOPMENT_SECRET.equals(secret)
                || secret.toLowerCase().contains("opspilot")
                || !secret.matches("[A-Za-z0-9_-]+")) {
            return false;
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (decoded.length < 32) {
            return false;
        }

        Set<Character> distinct = new HashSet<>();
        int[] frequencies = new int[128];
        for (char character : secret.toCharArray()) {
            if (character >= frequencies.length) {
                return false;
            }
            distinct.add(character);
            frequencies[character]++;
        }
        double entropyPerCharacter = 0;
        for (int frequency : frequencies) {
            if (frequency == 0) {
                continue;
            }
            double probability = (double) frequency / secret.length();
            entropyPerCharacter -= probability * (Math.log(probability) / Math.log(2));
        }

        return distinct.size() >= 20 && entropyPerCharacter >= 4.0 && !hasLongRepeatedRun(secret);
    }

    private static boolean hasLongRepeatedRun(String secret) {
        int runLength = 1;
        for (int index = 1; index < secret.length(); index++) {
            runLength = secret.charAt(index) == secret.charAt(index - 1) ? runLength + 1 : 1;
            if (runLength >= 8) {
                return true;
            }
        }
        return false;
    }
}
