package org.ngcvfb.apigateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/events")
    public Mono<ResponseEntity<Map<String, String>>> eventServiceFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Event Service is temporarily unavailable",
                        "message", "Please try again later"
                )));
    }

    @GetMapping("/users")
    public Mono<ResponseEntity<Map<String, String>>> userServiceFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "User Service is temporarily unavailable",
                        "message", "Please try again later"
                )));
    }

    @GetMapping("/default")
    public Mono<ResponseEntity<Map<String, String>>> defaultFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service is temporarily unavailable",
                        "message", "Please try again later"
                )));
    }
}
