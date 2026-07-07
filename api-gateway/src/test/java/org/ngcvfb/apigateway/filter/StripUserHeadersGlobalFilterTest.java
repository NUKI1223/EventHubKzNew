package org.ngcvfb.apigateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StripUserHeadersGlobalFilterTest {

    private final StripUserHeadersGlobalFilter filter = new StripUserHeadersGlobalFilter();

    @Test
    void removesAllSpoofedUserHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/events")
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .header("X-User-Email", "evil@x.kz")
                .header("X-User-Name", "evil")
                .header("X-Username", "evil")
                .header("X-Trace", "keep-me")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        var headers = forwarded.get().getHeaders();
        assertThat(headers.containsKey("X-User-Id")).isFalse();
        assertThat(headers.containsKey("X-User-Role")).isFalse();
        assertThat(headers.containsKey("X-User-Email")).isFalse();
        assertThat(headers.containsKey("X-User-Name")).isFalse();
        assertThat(headers.containsKey("X-Username")).isFalse();
        assertThat(headers.getFirst("X-Trace")).isEqualTo("keep-me"); // unrelated headers untouched
    }

    @Test
    void runsAtHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
