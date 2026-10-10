package com.docuvault.shared.validation;

/**
 * The password policy, in one place.
 *
 * <p>Registration and password-change both constrain the same thing, and two
 * copies of the same {@code @Size} annotation drift apart the first time one is
 * edited. These are compile-time constants so they can be used inside the
 * annotations themselves.
 *
 * <p>{@link #MAX_LENGTH} is not a style choice: BCrypt silently ignores
 * anything past 72 bytes, so accepting a longer password would mean the tail of
 * it never contributes to the hash.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 72;

    public static final String LENGTH_MESSAGE =
            "Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters";

    private PasswordRules() {
    }
}
