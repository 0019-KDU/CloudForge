package com.cloudforge.identity.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.cloudforge.common.security.CurrentUser;
import com.cloudforge.identity.api.IdentityDtos.UserResponse;
import com.cloudforge.identity.application.LoginThrottle;
import com.cloudforge.identity.application.ProfileService;
import com.cloudforge.identity.application.ProfileService.DirectoryEntry;
import com.cloudforge.identity.application.UserAdministrationService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own profile, and the colleague directory used to display names. */
@RestController
@RequestMapping("/api/users")
class MeController {

    private final UserAdministrationService users;
    private final ProfileService profiles;
    private final LoginThrottle throttle;

    MeController(UserAdministrationService users, ProfileService profiles, LoginThrottle throttle) {
        this.users = users;
        this.profiles = profiles;
        this.throttle = throttle;
    }

    record UpdateProfileRequest(@NotBlank @Size(max = 100) String displayName) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 256) String currentPassword,
            @NotBlank @Size(max = 256) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[<redacted>]";
        }
    }

    /** Read fresh from the database, so the profile reflects changes made after the token was issued. */
    @GetMapping("/me")
    UserResponse me(CurrentUser currentUser) {
        return UserResponse.from(users.requireUser(currentUser.id()));
    }

    @PutMapping("/me")
    UserResponse updateProfile(CurrentUser currentUser, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(profiles.updateDisplayName(currentUser.id(), request.displayName()));
    }

    /** Signs out every session (refresh tokens revoked); the client should send the user to sign in again. */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(CurrentUser currentUser, @Valid @RequestBody ChangePasswordRequest request) {
        // Limits guessing of the current password by someone holding a stolen access token.
        throttle.registerAttempt("password-change", currentUser.id().toString());
        profiles.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
    }

    /** {@code GET /api/users/directory?ids=a,b,c}: names for up to 100 user IDs in one call. */
    @GetMapping("/directory")
    List<DirectoryEntry> directory(CurrentUser currentUser, @RequestParam @NotEmpty Set<UUID> ids) {
        return profiles.directory(ids);
    }
}
