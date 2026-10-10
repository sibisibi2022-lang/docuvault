package com.docuvault.auth;

import com.docuvault.security.JwtService;
import com.docuvault.shared.audit.AuditAction;
import com.docuvault.shared.audit.AuditService;
import com.docuvault.shared.error.ConflictException;
import com.docuvault.user.Role;
import com.docuvault.user.User;
import com.docuvault.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with that email already exists");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(assignRole())
                .enabled(true)
                .build();

        userRepository.save(user);
        auditService.record(AuditAction.USER_REGISTERED, user, null, "Registered as " + user.getRole());
        log.info("Registered user {} with role {}", user.getId(), user.getRole());

        return AuthDtos.AuthResponse.of(jwtService.issueToken(user), jwtService.expiresInSeconds(), user);
    }

    /**
     * Role assignment is the server's decision, never the caller's.
     *
     * <p>The very first account to register becomes the administrator, so a
     * fresh deployment is usable without seeding a password into the repository.
     * Every account after that is an EMPLOYEE and can only be promoted by an
     * existing administrator.
     */
    private Role assignRole() {
        boolean noAdminYet = userRepository.countByRole(Role.ADMIN) == 0;
        return noAdminYet ? Role.ADMIN : Role.EMPLOYEE;
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        // Throws BadCredentialsException on a wrong password or unknown email;
        // the exception handler turns both into the same 401 so this endpoint
        // cannot be used to discover which emails are registered.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + email));

        if (!user.isEnabled()) {
            throw new DisabledException("This account has been disabled");
        }

        auditService.record(AuditAction.USER_LOGGED_IN, user, null, "Signed in");

        return AuthDtos.AuthResponse.of(jwtService.issueToken(user), jwtService.expiresInSeconds(), user);
    }
}
