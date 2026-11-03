package com.cloudforge.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cloudforge.config.JwtProperties;
import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues short-lived access tokens. Claims are kept minimal: no email or name, because JWTs are
 * only signed, not encrypted, and anyone holding one can read it.
 */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record AccessToken(String value, Instant issuedAt, Instant expiresAt) {

        public Duration lifetime() {
            return Duration.between(issuedAt, expiresAt);
        }

        @Override
        public String toString() {
            return "AccessToken[redacted, expiresAt=" + expiresAt + "]";
        }
    }

    public AccessToken issue(User user) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", user.getRoles().stream().map(Role::name).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, now, expiresAt);
    }
}
