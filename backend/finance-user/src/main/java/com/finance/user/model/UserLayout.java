package com.finance.user.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

    public static UserLayout emptyFor(String userSub) {
        return UserLayout.builder()
                .userSub(userSub)
                .overview(JsonNodeFactory.instance.objectNode())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
