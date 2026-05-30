package org.ngcvfb.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    private static final String KEY_PREFIX = "auth:verify:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RNG = new SecureRandom();

    private final StringRedisTemplate redis;

    public String issue(String email) {
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        redis.opsForValue().set(key(email), code, TTL);
        return code;
    }

    public boolean matches(String email, String code) {
        return Optional.ofNullable(redis.opsForValue().get(key(email)))
                .map(stored -> stored.equals(code))
                .orElse(false);
    }

    public void clear(String email) {
        redis.delete(key(email));
    }

    private static String key(String email) {
        return KEY_PREFIX + email.toLowerCase();
    }
}
