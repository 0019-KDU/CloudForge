package com.cloudforge.identity.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.identity.domain.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Invalidates every open token of a user, so only the newest one can ever be redeemed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now, t.version = t.version + 1 "
            + "where t.userId = :userId and t.usedAt is null")
    int invalidateOpenTokens(@Param("userId") UUID userId, @Param("now") Instant now);
}
