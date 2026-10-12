package com.docuvault.auth;

import com.docuvault.user.User;
import com.docuvault.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, String> requestReset(String email) {
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase();

            userRepository.findByEmailIgnoreCase(normalizedEmail)
                    .ifPresent(user -> {
                        tokenRepository.deleteByEmail(normalizedEmail);

                        String token = UUID.randomUUID().toString();
                        LocalDateTime expiry = LocalDateTime.now().plusHours(1);

                        tokenRepository.save(
                                new PasswordResetToken(
                                        token,
                                        normalizedEmail,
                                        expiry));

                        System.out.println(
                                "\n============================================");
                        System.out.println("PASSWORD RESET LINK (local dev only):");
                        System.out.println(
                                "http://localhost:4200/reset-password?token=" + token);
                        System.out.println("This link expires in 1 hour.");
                        System.out.println(
                                "============================================\n");
                    });
        }

        return Map.of(
                "message",
                "If that email address is registered, a reset link has been sent.");
    }

    @Transactional
    public Map<String, String> resetPassword(
            String token,
            String newPassword) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required.");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "The reset link is invalid or has already been used."));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException(
                    "This reset link has already been used.");
        }

        if (LocalDateTime.now().isAfter(resetToken.getExpiresAt())) {
            throw new IllegalArgumentException(
                    "This reset link has expired. Please request a new one.");
        }

        User user = userRepository
                .findByEmailIgnoreCase(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return Map.of(
                "message",
                "Your password has been reset successfully.");
    }
}