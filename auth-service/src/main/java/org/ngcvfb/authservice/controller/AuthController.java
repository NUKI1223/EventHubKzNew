package org.ngcvfb.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ngcvfb.authservice.dto.AuthResponse;
import org.ngcvfb.authservice.dto.LoginRequest;
import org.ngcvfb.authservice.dto.SignupRequest;
import org.ngcvfb.authservice.model.AuthUser;
import org.ngcvfb.authservice.service.AuthenticationService;
import org.ngcvfb.authservice.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    @Value("${security.cookie.secure:false}")
    private boolean cookieSecure;

    private static final String REFRESH_COOKIE = "refresh_token";

    private ResponseCookie refreshCookie(String token, long maxAgeSec) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/auth").maxAge(maxAgeSec).build();
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            AuthUser user = authenticationService.signup(request);
            return ResponseEntity.ok(Map.of(
                    "message", "User registered successfully. Please check your email for verification code.",
                    "email", user.getEmail()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authenticationService.authenticate(request);
            String refresh = refreshTokenService.issue(response.getUserId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh, refreshTokenService.ttlSeconds()).toString())
                    .body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Rotate the refresh token (cookie) and return a new short-lived access token. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        Long userId = refreshTokenService.validate(refreshToken);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }
        AuthResponse response = authenticationService.buildResponse(authenticationService.getById(userId));
        refreshTokenService.revoke(refreshToken);            // rotation: old token dies
        String newRefresh = refreshTokenService.issue(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(newRefresh, refreshTokenService.ttlSeconds()).toString())
                .body(response);
    }

    /** Revoke the refresh token and clear the cookie. Idempotent. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
                .body(Map.of("message", "Logged out"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("verificationCode");
            authenticationService.verifyUser(email, code);
            return ResponseEntity.ok(Map.of("message", "Account verified successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            authenticationService.resendVerificationCode(email);
            return ResponseEntity.ok(Map.of("message", "Verification code sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> userExists(@RequestParam String email) {
        return ResponseEntity.ok(authenticationService.userExists(email));
    }
}
