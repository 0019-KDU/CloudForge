package com.cloudforge.identity.repository;

import java.util.Optional;
import java.util.UUID;

import com.cloudforge.identity.domain.Invitation;
import com.cloudforge.identity.domain.InvitationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    Optional<Invitation> findByTokenHash(String tokenHash);

    Page<Invitation> findByStatus(InvitationStatus status, Pageable pageable);
}
