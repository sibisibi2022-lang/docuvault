package com.docuvault.user;

import com.docuvault.shared.validation.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    /** Never carries the password hash — no endpoint has any reason to return it. */
    public record UserResponse(
            Long id,
            String name,
            String email,
            Role role,
            boolean enabled,
            Instant createdAt) {

        static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(), user.getName(), user.getEmail(),
                    user.getRole(), user.isEnabled(), user.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 120, message = "Name must be at most 120 characters")
            String name) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Your current password is required")
            String currentPassword,

            @NotBlank(message = "A new password is required")
            @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH,
                    message = PasswordRules.LENGTH_MESSAGE)
            String newPassword) {
    }

    /** Admin-only. This is the single supported way a role ever changes. */
    public record ChangeRoleRequest(@NotNull(message = "Role is required") Role role) {
    }

    public record SetEnabledRequest(boolean enabled) {
    }
}
