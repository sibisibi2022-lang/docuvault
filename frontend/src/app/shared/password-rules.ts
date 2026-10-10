/**
 * The client's copy of the server's password policy.
 *
 * <p>Mirrors PasswordRules.java. Keeping the numbers in one place per side
 * stops the form from accepting something the API will reject a round trip
 * later, which reads to the user as the app being broken.
 */
export const PASSWORD_MIN_LENGTH = 5;

/** BCrypt ignores input past 72 bytes, so a longer password is partly decorative. */
export const PASSWORD_MAX_LENGTH = 72;

export const PASSWORD_HINT = `At least ${PASSWORD_MIN_LENGTH} characters.`;
