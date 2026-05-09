package com.finance.user.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.finance.common.model.TrackedAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "user_chart_drawings",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_chart_drawings",
                columnNames = {"user_sub", "tracked_asset_id"}),
        indexes = {
                @Index(name = "idx_user_chart_drawings_user", columnList = "user_sub"),
                @Index(name = "idx_user_chart_drawings_tracked", columnList = "tracked_asset_id")
        })
public class UserChartDrawing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracked_asset_id", nullable = false)
    private TrackedAsset trackedAsset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "drawings", nullable = false, columnDefinition = "jsonb")
    private JsonNode drawings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (drawings == null) drawings = JsonNodeFactory.instance.arrayNode();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
