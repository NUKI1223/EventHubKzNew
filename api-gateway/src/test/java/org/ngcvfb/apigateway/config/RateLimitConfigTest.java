package org.ngcvfb.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    private final RateLimitConfig config = new RateLimitConfig();
    private final KeyResolver ip = config.ipKeyResolver();
    private final KeyResolver user = config.userKeyResolver();

    @Test
    void ipResolverPrefersFirstXForwardedForEntry() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"));
        assertThat(ip.resolve(exchange).block()).isEqualTo("203.0.113.7");
    }

    @Test
    void ipResolverFallsBackToRemoteAddressWhenNoHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/auth/login").remoteAddress(
                        new java.net.InetSocketAddress("192.168.1.50", 12345)));
        assertThat(ip.resolve(exchange).block()).isEqualTo("192.168.1.50");
    }

    @Test
    void ipResolverNeverReturnsEmpty() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login"));
        String key = ip.resolve(exchange).block();
        assertThat(key).isNotBlank();
    }

    @Test
    void userResolverUsesUserIdHeader() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/support/chat").header("X-User-Id", "42"));
        assertThat(user.resolve(exchange).block()).isEqualTo("42");
    }

    @Test
    void userResolverFallsBackToIpWhenNoUserId() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/support/chat")
                        .header("X-Forwarded-For", "203.0.113.9"));
        assertThat(user.resolve(exchange).block()).isEqualTo("203.0.113.9");
    }
}
