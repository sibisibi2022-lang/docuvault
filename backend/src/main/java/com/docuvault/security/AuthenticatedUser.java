package com.docuvault.security;

import com.docuvault.user.Role;
import com.docuvault.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated caller, as the rest of the application sees them.
 *
 * <p>Controllers receive this via {@code @AuthenticationPrincipal}, which is the
 * only trustworthy source of "who is calling". The original read the role from
 * {@code localStorage} in the browser and from a database lookup keyed on a
 * token subject; this type carries the identity the server itself verified.
 *
 * @param password the BCrypt hash, present only during login. Null on every
 *                 token-authenticated request, since nothing downstream needs it.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String name,
        Role role,
        String password,
        boolean enabled) implements UserDetails {

    public static AuthenticatedUser forLogin(User user) {
        return new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getPassword(), user.isEnabled());
    }

    public static AuthenticatedUser fromToken(Long id, String email, String name, Role role) {
        return new AuthenticatedUser(id, email, name, role, null, true);
    }

    /** True for roles that may act across the whole workspace, not just their own rows. */
    public boolean canActOnAnyDocument() {
        return role == Role.ADMIN || role == Role.MANAGER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(role.authority());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
