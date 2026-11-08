package com.opspilot.auth.ratelimit;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

public class ClientAddressResolver {
    private final Set<String> trustedProxies;

    public ClientAddressResolver(String trustedProxyAddresses) {
        this.trustedProxies = Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ClientAddressResolver::canonicalizeIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = canonicalizeOrOriginal(request.getRemoteAddr());
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String[] chain = forwardedFor.split(",", -1);
        String selected = remoteAddress;
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate;
            try {
                candidate = canonicalizeIp(chain[index].trim());
            } catch (IllegalArgumentException exception) {
                return remoteAddress;
            }
            selected = candidate;
            if (!trustedProxies.contains(candidate)) {
                return candidate;
            }
        }
        return selected;
    }

    static String canonicalizeIp(String address) {
        if (address == null || address.isBlank() || address.contains("%")) {
            throw new IllegalArgumentException("Invalid IP address");
        }
        if (address.indexOf(':') >= 0) {
            if (!address.matches("[0-9A-Fa-f:.]+")) {
                throw new IllegalArgumentException("Invalid IP address");
            }
            try {
                return InetAddress.getByName(address).getHostAddress().toLowerCase();
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid IP address");
            }
        }

        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address");
        }
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}")) {
                throw new IllegalArgumentException("Invalid IP address");
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                throw new IllegalArgumentException("Invalid IP address");
            }
            if (!normalized.isEmpty()) {
                normalized.append('.');
            }
            normalized.append(octet);
        }
        return normalized.toString();
    }

    private static String canonicalizeOrOriginal(String address) {
        try {
            return canonicalizeIp(address);
        } catch (IllegalArgumentException exception) {
            return "unknown";
        }
    }
}
