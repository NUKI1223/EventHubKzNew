package org.ngcvfb.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CorsConfig {

    // Список origin через запятую. В проде фронт и API одного домена (за Caddy),
    // поэтому значение остаётся пустым и cross-origin не разрешается вовсе.
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    static CorsConfiguration buildCorsConfiguration(String originsCsv) {
        List<String> origins = originsCsv == null ? List.of()
                : Arrays.stream(originsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setMaxAge(3600L);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(Collections.singletonList("*"));
        cfg.setAllowCredentials(true);
        return cfg;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", buildCorsConfiguration(allowedOrigins));
        return new CorsWebFilter(source);
    }
}
