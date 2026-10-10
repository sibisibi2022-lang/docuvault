package com.docuvault.security;

import com.docuvault.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Used only by the login flow. Token-authenticated requests build their
 * principal from signed claims and never reach this class.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(AuthenticatedUser::forLogin)
                // The message is intentionally generic and never echoed to the
                // caller: Spring turns it into the same "Invalid email or
                // password" response a wrong password produces.
                .orElseThrow(() -> new UsernameNotFoundException("No account for the supplied credentials"));
    }
}
