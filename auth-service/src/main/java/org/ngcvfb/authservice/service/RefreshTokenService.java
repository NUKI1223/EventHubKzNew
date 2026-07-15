package org.ngcvfb.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Opaque, revocable refresh tokens backed by Redis (key {@code refresh:<token> -> userId},
 * TTL from config). The token itself is the secret — high-entropy random, like a session id.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String PREFIX = "refresh:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${security.refresh.expiration:604800000}")
    private long ttlMs;

    /** Mint a new refresh token for the user and store it with a TTL. */
    public String issue(Long userId) {
        String token = generate();
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofMillis(ttlMs));
        return token;
    }

    /** Return the userId a refresh token maps to, or null if unknown/expired. */
    public Long validate(String token) {
        if (token == null || token.isBlank()) return null;
        String v = redis.opsForValue().get(PREFIX + token);
        return v == null ? null : Long.valueOf(v);
    }

    /** Revoke a refresh token (logout, or the old token during rotation). Idempotent. */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) redis.delete(PREFIX + token);
    }

    public long ttlSeconds() {
        return ttlMs / 1000;
    }

    private String generate() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
