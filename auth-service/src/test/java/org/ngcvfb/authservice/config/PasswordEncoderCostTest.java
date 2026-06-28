package org.ngcvfb.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderCostTest {

    @Test
    void cost10EncoderVerifiesLegacyCost12HashAndEncodesAtCost10() {
        String legacyCost12Hash = new BCryptPasswordEncoder(12).encode("password123");
        assertTrue(legacyCost12Hash.startsWith("$2a$12$"), "precondition: legacy hash is cost 12");

        BCryptPasswordEncoder current = new BCryptPasswordEncoder(10);

        // existing cost-12 password must still verify (no DB migration)
        assertTrue(current.matches("password123", legacyCost12Hash));
        // new passwords are encoded at cost 10
        assertTrue(current.encode("password123").startsWith("$2a$10$"));
    }
}
