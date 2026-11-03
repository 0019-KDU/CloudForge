package com.cloudforge.identity.application;

import java.util.Set;

import com.cloudforge.identity.domain.EmailAddress;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;
import com.cloudforge.identity.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first platform administrator, so a fresh installation can be used at all (there is no
 * open registration). It does nothing unless {@code cloudforge.bootstrap.admin.email} is set, or once
 * any active administrator exists. It never grants admin rights to an existing account.
 *
 * <p>Supply the values through environment variables and remove them after the first start.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository users,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @param email       CLOUDFORGE_BOOTSTRAP_ADMIN_EMAIL
     * @param password    CLOUDFORGE_BOOTSTRAP_ADMIN_PASSWORD (must satisfy the password policy)
     * @param displayName CLOUDFORGE_BOOTSTRAP_ADMIN_DISPLAY_NAME
     */
    @ConfigurationProperties("cloudforge.bootstrap.admin")
    public record BootstrapAdminProperties(String email, String password, String displayName) {

        @Override
        public String toString() {
            return "BootstrapAdminProperties[email=" + email + ", password=<redacted>]";
        }
    }

    /**
     * Deliberately not {@code @Transactional}: each repository call commits on its own, so a duplicate
     * insert by a concurrently starting instance can be caught without poisoning a surrounding transaction.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (properties.email() == null || properties.email().isBlank()) {
            return;
        }
        if (users.existsWithRoleAndStatus(Role.PLATFORM_ADMIN, UserStatus.ACTIVE)) {
            log.info("Bootstrap admin skipped: an active platform administrator already exists");
            return;
        }
        String email = EmailAddress.normalize(properties.email());
        if (users.existsByEmail(email)) {
            log.warn("Bootstrap admin skipped: {} already exists and will not be promoted automatically", email);
            return;
        }
        passwordPolicy.validate(properties.password(), email);
        String displayName = properties.displayName() == null || properties.displayName().isBlank()
                ? "Platform Administrator" : properties.displayName();
        try {
            users.saveAndFlush(new User(email, displayName, passwordEncoder.encode(properties.password()),
                    Set.of(Role.PLATFORM_ADMIN)));
            log.info("Bootstrap admin created: {}", email);
        } catch (DataIntegrityViolationException e) {
            log.info("Bootstrap admin already created by another instance");
        }
    }
}
