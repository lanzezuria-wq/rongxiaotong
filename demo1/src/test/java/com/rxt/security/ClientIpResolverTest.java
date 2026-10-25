package com.rxt.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    @Test
    void directClientsCannotChooseTheirRateLimitKey() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        assertEquals("198.51.100.42", new ClientIpResolver("").resolve(request));
    }

    @Test
    void walkProxyChainFromTheTrustedEnd() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.42, 10.0.0.2");
        assertEquals("198.51.100.42", new ClientIpResolver("127.0.0.1,10.0.0.0/24").resolve(request));
    }

    @Test
    void malformedForwardedAddressesFallBackToThePeer() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "not-an-ip");
        assertEquals("127.0.0.1", new ClientIpResolver("127.0.0.1").resolve(request));
    }
}
