package com.finance.user.model;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A user's persisted dashboard layout (the configurable overview page), keyed by Keycloak subject
 * and stored as opaque JSON whose structure is owned by the frontend. One row per user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_layouts")
public class UserLayout {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "overview", nullable = false, columnDefinition = "jsonb")
    private JsonNode overview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (overview == null) overview = JsonNodeFactory.instance.objectNode();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Builds an empty layout row (no widgets placed) for a user who has not yet customized their overview. */
    public static UserLayout emptyFor(String userSub) {
        return UserLayout.builder()
                .userSub(userSub)
                .overview(JsonNodeFactory.instance.objectNode())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
