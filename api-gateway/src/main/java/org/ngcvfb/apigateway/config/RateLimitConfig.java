package org.ngcvfb.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    // За Caddy реальный IP клиента — в X-Forwarded-For (первый элемент), а не в сокете.
    // В dev без Caddy заголовка нет — падаем на remoteAddress, в крайнем случае "unknown".
    static String clientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        if (addr != null && addr.getAddress() != null) {
            return addr.getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(userId != null && !userId.isBlank() ? userId : clientIp(exchange));
        };
    }
}
