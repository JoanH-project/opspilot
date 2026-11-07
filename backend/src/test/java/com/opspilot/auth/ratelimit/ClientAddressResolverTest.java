package com.opspilot.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {
    @Test
    void ignoresForwardedAddressUnlessImmediatePeerIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.12");
        request.addHeader("X-Forwarded-For", "203.0.113.8");
        assertEquals("198.51.100.12", new ClientAddressResolver("10.0.0.10").resolve(request));
    }

    @Test
    void resolvesClientFromRightmostUntrustedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.8, 10.0.0.11");
        assertEquals("203.0.113.8", new ClientAddressResolver("10.0.0.10,10.0.0.11").resolve(request));
    }

    @Test
    void rejectsInvalidTrustedProxyConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ClientAddressResolver("proxy.example.com"));
    }
}
