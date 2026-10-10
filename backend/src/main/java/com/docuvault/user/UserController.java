package com.docuvault.user;

import com.docuvault.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // -------------------------------------------------------------- own account

    @GetMapping("/me")
    public UserDtos.UserResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
        return userService.me(caller);
    }

    @PutMapping("/me")
    public UserDtos.UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser caller,
                                               @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return userService.updateOwnProfile(caller, request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change your own password")
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser caller,
                               @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changeOwnPassword(caller, request);
    }

    // ------------------------------------------------------------ administration

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserDtos.UserResponse> list(@RequestParam(required = false) String search,
                                            @PageableDefault(size = 25, sort = "name") Pageable pageable) {
        return userService.list(search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a user's role",
            description = "The only supported way a role changes. Registration never grants one.")
    public UserDtos.UserResponse changeRole(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @PathVariable Long id,
                                            @Valid @RequestBody UserDtos.ChangeRoleRequest request) {
        return userService.changeRole(caller, id, request.role());
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable an account",
            description = "Disabling is preferred over deletion: it preserves the audit trail and document ownership.")
    public UserDtos.UserResponse setEnabled(@AuthenticationPrincipal AuthenticatedUser caller,
                                            @PathVariable Long id,
                                            @RequestBody UserDtos.SetEnabledRequest request) {
        return userService.setEnabled(caller, id, request.enabled());
    }
}
