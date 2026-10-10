package com.docuvault.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The only roles that exist. Making this an enum rather than a free-text column
 * is what stops "Admin", "admin " and "ADMNI" from all ending up in the
 * database and silently failing every authorization check.
 */
public enum Role {

    /** Full access, including user management and role changes. */
    ADMIN,

    /** Manages documents, categories and audit history for the whole workspace. */
    MANAGER,

    /** Works with their own documents only. */
    EMPLOYEE;

    public GrantedAuthority authority() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }

    public static Role from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Role is required");
        }
        return Role.valueOf(raw.trim().toUpperCase());
    }
}
