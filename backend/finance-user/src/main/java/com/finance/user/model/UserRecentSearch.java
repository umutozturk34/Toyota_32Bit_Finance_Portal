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
 * A user's most-recently selected search results, keyed by Keycloak subject and stored as a JSON
 * array (newest first) whose element shape is owned by the application. One row per user.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_recent_searches")
public class UserRecentSearch {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", nullable = false, columnDefinition = "jsonb")
    private JsonNode items;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) updatedAt = Instant.now();
        if (items == null) items = JsonNodeFactory.instance.arrayNode();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    /** Builds an empty recent-searches row (no entries yet) for a user who has not selected any result. */
    public static UserRecentSearch emptyFor(String userSub) {
        return UserRecentSearch.builder()
                .userSub(userSub)
                .items(JsonNodeFactory.instance.arrayNode())
                .updatedAt(Instant.now())
                .build();
    }
}
