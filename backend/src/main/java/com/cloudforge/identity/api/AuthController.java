package com.cloudforge.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.cloudforge.identity.application.AuthenticationService;
import com.cloudforge.identity.application.AuthenticationService.Tokens;
import com.cloudforge.identity.application.LoginThrottle;
import com.cloudforge.identity.application.PasswordResetService;
import com.cloudforge.identity.application.RefreshTokenService.IssuedRefreshToken;
import com.cloudforge.identity.application.RegistrationService;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token endpoints (ADR-0003).
 *
 * <p>The access token goes in the JSON body; the client keeps it in memory and sends it as
 * {@code Authorization: Bearer}. The refresh token goes only in an {@code HttpOnly; Secure;
 * SameSite=Strict} cookie scoped to {@code /api/auth}, so page scripts cannot read it and the browser
 * sends it nowhere else. Every token response is {@code no-store}.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    static final String REFRESH_COOKIE = "cf_refresh";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthenticationService authentication;
    private final RegistrationService registration;
    private final LoginThrottle throttle;
    private final PasswordResetService passwordResets;
    private final Clock clock;

    AuthController(AuthenticationService authentication, RegistrationService registration, LoginThrottle throttle,
            PasswordResetService passwordResets, Clock clock) {
        this.authentication = authentication;
        this.registration = registration;
        this.throttle = throttle;
        this.passwordResets = passwordResets;
        this.clock = clock;
    }

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 256) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=<redacted>]";
        }
    }

    record RegisterRequest(@NotBlank @Size(max = 128) String invitationToken,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 256) String password) {

        @Override
        public String toString() {
            return "RegisterRequest[displayName=" + displayName + ", invitationToken=<redacted>, password=<redacted>]";
        }
    }

    record PasswordResetRequest(@NotBlank @Size(max = 128) String token, @NotBlank @Size(max = 256) String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetRequest[<redacted>]";
        }
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    record RegisteredUserResponse(UUID id, String email, String displayName, Set<Role> roles) {

        static RegisteredUserResponse from(User user) {
            return new RegisteredUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRoles());
        }
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        throttle.registerAttempt("login", http.getRemoteAddr());
        return tokenResponse(authentication.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return tokenResponse(authentication.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authentication.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/register")
    ResponseEntity<RegisteredUserResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        throttle.registerAttempt("register", http.getRemoteAddr());
        User user = registration.register(request.invitationToken(), request.displayName(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegisteredUserResponse.from(user));
    }

    /** Redeems an administrator-issued reset token; the user then signs in with the new password. */
    @PostMapping("/password-reset")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        throttle.registerAttempt("password-reset", http.getRemoteAddr());
        passwordResets.redeem(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(Tokens tokens) {
        IssuedRefreshToken refresh = tokens.refreshToken();
        Duration refreshLifetime = Duration.between(Instant.now(clock), refresh.expiresAt());
        long expiresIn = tokens.accessToken().lifetime().toSeconds();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh.token().value(), refreshLifetime).toString())
                .body(new TokenResponse(tokens.accessToken().value(), "Bearer", expiresIn));
    }

    private static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
