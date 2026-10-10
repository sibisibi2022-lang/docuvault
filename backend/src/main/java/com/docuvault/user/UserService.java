package com.docuvault.user;

import com.docuvault.security.AuthenticatedUser;
import com.docuvault.shared.audit.AuditAction;
import com.docuvault.shared.audit.AuditService;
import com.docuvault.shared.error.BadRequestException;
import com.docuvault.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse me(AuthenticatedUser caller) {
        return UserDtos.UserResponse.from(require(caller.id()));
    }

    @Transactional
    public UserDtos.UserResponse updateOwnProfile(AuthenticatedUser caller, UserDtos.UpdateProfileRequest request) {
        User user = require(caller.id());
        // Name only. Email is the login identifier and the role is an
        // administrator's decision, so neither is editable from here.
        user.setName(request.name().trim());
        return UserDtos.UserResponse.from(user);
    }

    @Transactional
    public void changeOwnPassword(AuthenticatedUser caller, UserDtos.ChangePasswordRequest request) {
        User user = require(caller.id());

        // Proving knowledge of the current password is what stops a stolen token
        // from being upgraded into permanent account takeover.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Your current password is not correct");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        log.info("User {} changed their password", user.getId());
    }

    @Transactional(readOnly = true)
    public Page<UserDtos.UserResponse> list(String search, Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return userRepository.search(term, pageable).map(UserDtos.UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(Long id) {
        return UserDtos.UserResponse.from(require(id));
    }

    @Transactional
    public UserDtos.UserResponse changeRole(AuthenticatedUser caller, Long id, Role newRole) {
        User target = require(id);
        Role previous = target.getRole();

        if (previous == newRole) {
            return UserDtos.UserResponse.from(target);
        }

        // Losing the last administrator would lock everyone out of user
        // management with no way back in, so that transition is refused.
        if (previous == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("This is the only administrator. Promote someone else first.");
        }

        target.setRole(newRole);

        auditService.record(AuditAction.USER_ROLE_CHANGED, require(caller.id()), null,
                "Changed %s from %s to %s".formatted(target.getEmail(), previous, newRole));
        log.info("Admin {} changed user {} from {} to {}", caller.id(), id, previous, newRole);

        return UserDtos.UserResponse.from(target);
    }

    @Transactional
    public UserDtos.UserResponse setEnabled(AuthenticatedUser caller, Long id, boolean enabled) {
        User target = require(id);

        if (target.getId().equals(caller.id())) {
            throw new BadRequestException("You cannot disable your own account");
        }
        if (!enabled && target.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("This is the only administrator and cannot be disabled");
        }

        target.setEnabled(enabled);

        auditService.record(AuditAction.USER_DISABLED, require(caller.id()), null,
                (enabled ? "Enabled " : "Disabled ") + target.getEmail());

        return UserDtos.UserResponse.from(target);
    }

    private User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
