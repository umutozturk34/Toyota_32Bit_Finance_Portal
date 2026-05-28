package com.finance.common.repository;

import com.finance.common.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data access for {@link UserStatus}, the local mirror of Keycloak enabled-state keyed by JWT
 * subject.
 */
public interface UserStatusRepository extends JpaRepository<UserStatus, String> {

    /**
     * Atomically inserts or updates a user's enabled flag (Postgres {@code ON CONFLICT} upsert),
     * stamping {@code updated_at} with the DB clock.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_status (user_sub, enabled, updated_at)
            VALUES (:userSub, :enabled, now())
            ON CONFLICT (user_sub) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                updated_at = now()
            """, nativeQuery = true)
    void upsertEnabled(@Param("userSub") String userSub, @Param("enabled") boolean enabled);
}
