package com.cloudforge.identity.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Identity settings under {@code cloudforge.identity}.
 *
 * @param invitationTtl how long an invitation token stays valid
 */
@ConfigurationProperties("cloudforge.identity")
public record IdentityProperties(@DefaultValue("P7D") Duration invitationTtl) {
}
