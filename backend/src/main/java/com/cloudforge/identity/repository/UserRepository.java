package com.cloudforge.identity.repository;

import java.util.Optional;
import java.util.UUID;

import com.cloudforge.identity.domain.Role;
import com.cloudforge.identity.domain.User;
import com.cloudforge.identity.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Expects an already normalized email (see {@code EmailAddress}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByManagerIdAndStatus(UUID managerId, UserStatus status);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role and u.status = :status")
    boolean existsWithRoleAndStatus(@Param("role") Role role, @Param("status") UserStatus status);

    @Query("""
            select u from User u
            where (:status is null or u.status = :status)
              and (:teamId is null or u.teamId = :teamId)
              and (:managerId is null or u.managerId = :managerId)
            """)
    Page<User> search(@Param("status") UserStatus status, @Param("teamId") UUID teamId,
            @Param("managerId") UUID managerId, Pageable pageable);

    /**
     * Whether {@code userId} appears in the management chain above {@code managerId} (including
     * {@code managerId} itself). Making {@code managerId} the manager of {@code userId} would then
     * create a cycle. {@code UNION} (not {@code UNION ALL}) stops the recursion even if bad data
     * already contains a cycle.
     */
    @Query(value = """
            WITH RECURSIVE chain(id, manager_id) AS (
                SELECT id, manager_id FROM app_user WHERE id = :managerId
                UNION
                SELECT u.id, u.manager_id FROM app_user u JOIN chain c ON u.id = c.manager_id
            )
            SELECT EXISTS (SELECT 1 FROM chain WHERE id = :userId)
            """, nativeQuery = true)
    boolean isInManagementChain(@Param("managerId") UUID managerId, @Param("userId") UUID userId);
}
