package org.ngcvfb.authservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @InjectMocks RefreshTokenService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "ttlMs", 604800000L);
    }

    @Test
    void issueStoresTokenAndValidateReturnsUserId() {
        when(redis.opsForValue()).thenReturn(ops);
        String token = service.issue(42L);
        assertThat(token).isNotBlank();
        verify(ops).set(startsWith("refresh:"), eq("42"), any(Duration.class));
        when(ops.get("refresh:" + token)).thenReturn("42");
        assertThat(service.validate(token)).isEqualTo(42L);
    }

    @Test
    void validateUnknownTokenReturnsNull() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn(null);
        assertThat(service.validate("nope")).isNull();
    }

    @Test
    void validateNullOrBlankReturnsNullWithoutRedis() {
        assertThat(service.validate(null)).isNull();
        assertThat(service.validate("  ")).isNull();
        verifyNoInteractions(redis);
    }

    @Test
    void revokeDeletesKey() {
        service.revoke("tok");
        verify(redis).delete("refresh:tok");
    }
}
