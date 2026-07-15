package org.ngcvfb.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ngcvfb.authservice.dto.AuthResponse;
import org.ngcvfb.authservice.dto.LoginRequest;
import org.ngcvfb.authservice.dto.SignupRequest;
import org.ngcvfb.authservice.model.AuthUser;
import org.ngcvfb.authservice.model.Role;
import org.ngcvfb.authservice.repository.AuthUserRepository;
import org.ngcvfb.eventhubkz.common.events.UserRegisteredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final AuthUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final VerificationCodeStore codeStore;

    @Transactional
    public AuthUser signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        AuthUser user = AuthUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .role(Role.USER)
                .enabled(false)
                .build();

        user = userRepository.save(user);

        String code = codeStore.issue(user.getEmail());
        sendVerificationEmail(user.getEmail(), code);

        publishUserRegisteredEvent(user);

        log.info("User registered: {}", user.getEmail());
        return user;
    }

    public AuthResponse authenticate(LoginRequest request) {
        AuthUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEnabled()) {
            throw new RuntimeException("Account not verified. Please verify your account.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        request.getPassword()
                )
        );

        log.info("User authenticated: {}", user.getEmail());
        return buildResponse(user);
    }

    /** Build a fresh access-token response for a user (used by login and by /auth/refresh). */
    public AuthResponse buildResponse(AuthUser user) {
        return AuthResponse.builder()
                .token(jwtService.generateToken(user))
                .expiresIn(jwtService.getExpirationTime())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    public AuthUser getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void verifyUser(String email, String code) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!codeStore.matches(email, code)) {
            throw new RuntimeException("Invalid or expired verification code");
        }

        user.setEnabled(true);
        userRepository.save(user);
        codeStore.clear(email);

        log.info("User verified: {}", email);
    }

    @Transactional
    public void resendVerificationCode(String email) {
        AuthUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEnabled()) {
            throw new RuntimeException("Account is already verified");
        }

        String code = codeStore.issue(email);
        sendVerificationEmail(email, code);
        log.info("Verification code resent to: {}", email);
    }

    public boolean userExists(String email) {
        return userRepository.existsByEmail(email);
    }

    private void sendVerificationEmail(String email, String code) {
        String subject = "EventHubKz - Account Verification";
        String htmlMessage = buildVerificationEmailHtml(code);

        try {
            emailService.sendVerificationEmail(email, subject, htmlMessage);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", email, e);
        }
    }

    private String buildVerificationEmailHtml(String code) {
        return "<html>"
                + "<body style=\"font-family: Arial, sans-serif;\">"
                + "<div style=\"background-color: #f5f5f5; padding: 20px;\">"
                + "<h2 style=\"color: #333;\">Welcome to EventHubKz!</h2>"
                + "<p style=\"font-size: 16px;\">Please enter the verification code below to continue:</p>"
                + "<div style=\"background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">"
                + "<h3 style=\"color: #333;\">Verification Code:</h3>"
                + "<p style=\"font-size: 24px; font-weight: bold; color: #007bff; letter-spacing: 3px;\">" + code + "</p>"
                + "</div>"
                + "<p style=\"font-size: 14px; color: #666; margin-top: 20px;\">This code will expire in 10 minutes.</p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    private void publishUserRegisteredEvent(AuthUser user) {
        try {
            UserRegisteredEvent event = UserRegisteredEvent.create(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail()
            );
            kafkaTemplate.send("user.registered", user.getId().toString(), event);
            log.info("Published user.registered event for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to publish user.registered event", e);
        }
    }
}
