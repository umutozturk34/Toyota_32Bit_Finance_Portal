package com.finance.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A pending email-change verification, one per user (keyed by Keycloak subject). Holds the target
 * address, a hashed confirmation code, the attempt counter (for rate limiting) and an expiry; the
 * change is applied only once the user confirms a matching, non-expired code.
 */
@Entity
@Table(name = "email_change_request")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@Builder
public class EmailChangeRequest {

    @Id
    @Column(name = "user_sub", length = 64)
    private String userSub;

    @Column(name = "new_email", nullable = false, length = 255)
    private String newEmail;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
