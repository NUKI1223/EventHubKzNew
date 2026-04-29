package org.ngcvfb.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ngcvfb.authservice.dto.AuthResponse;
import org.ngcvfb.authservice.dto.LoginRequest;
import org.ngcvfb.authservice.dto.SignupRequest;
import org.ngcvfb.authservice.model.AuthUser;
import org.ngcvfb.authservice.service.AuthenticationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

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
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
