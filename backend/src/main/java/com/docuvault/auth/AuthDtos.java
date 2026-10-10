package com.docuvault.auth;

import com.docuvault.user.Role;
import com.docuvault.shared.validation.PasswordRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Note what is absent: there is no {@code role} field.
     *
     * <p>The original accepted the role from this body and wrote it straight to
     * the user row, so {@code {"role":"ADMIN"}} on the public registration
     * endpoint granted administrator rights to anyone who asked. Roles are now
     * assigned by the server and changed only by an administrator.
     */
    public record RegisterRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 120, message = "Name must be at most 120 characters")
            String name,

            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH,
                    message = PasswordRules.LENGTH_MESSAGE)
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password) {
    }

    /**
     * The role is returned so the UI can render the right navigation, but the
     * browser's copy is a convenience only — every request is authorised server
     * side from the signed token, never from what the client claims to be.
     */
    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            Long userId,
            String name,
            String email,
            Role role) {

        public static AuthResponse of(String token, long expiresIn, com.docuvault.user.User user) {
            return new AuthResponse(token, "Bearer", expiresIn,
                    user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }
}
