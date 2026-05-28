package com.finance.common.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Local mirror of a Keycloak user's enabled/disabled state, keyed by the JWT subject, used to
 * authorize requests without a round-trip to the identity provider.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_status")
public class UserStatus {

    @Id
    @Column(name = "user_sub", length = 64)
    private String userSub;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
