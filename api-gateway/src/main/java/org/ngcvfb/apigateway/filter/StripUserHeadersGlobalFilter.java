package org.ngcvfb.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Удаляет клиентские X-User-* из КАЖДОГО входящего запроса до любого роут-фильтра.
 * AuthenticationFilter затем ставит подлинные заголовки на авторизованных роутах;
 * публичные роуты доходят до downstream без этих заголовков — подделать личность нельзя.
 */
@Component
public class StripUserHeadersGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> USER_HEADERS = List.of(
            "X-User-Id", "X-User-Name", "X-Username", "X-User-Email", "X-User-Role");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> USER_HEADERS.forEach(h::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
