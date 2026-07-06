package org.ngcvfb.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void parsesCommaSeparatedOrigins() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration(
                "http://localhost:5173,https://eventhub.kz");
        assertThat(cfg.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://eventhub.kz");
        assertThat(cfg.getAllowCredentials()).isTrue();
        assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
    }

    @Test
    void trimsWhitespaceAndDropsBlanks() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration(" http://a.com , , http://b.com ");
        assertThat(cfg.getAllowedOrigins()).containsExactly("http://a.com", "http://b.com");
    }

    @Test
    void blankInputYieldsEmptyOrigins() {
        CorsConfiguration cfg = CorsConfig.buildCorsConfiguration("");
        assertThat(cfg.getAllowedOrigins()).isEmpty();
    }
}
