package com.finance.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent registry of a tradable/observable asset, uniquely identified by its
 * ({@link MarketType}, asset code) pair. Instances are created active and may be soft-disabled via
 * {@link #deactivate()} rather than deleted, preserving referential history.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "instruments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_instruments_type_code", columnNames = {"instrument_type", "asset_code"})
        },
        indexes = {
                @Index(name = "idx_instruments_instrument_type", columnList = "instrument_type"),
                @Index(name = "idx_instruments_active", columnList = "active")
        })
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 16)
    private MarketType marketType;

    @Column(name = "asset_code", nullable = false, length = 100)
    private String assetCode;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public static Instrument create(MarketType marketType, String assetCode) {
        return Instrument.builder()
                .marketType(marketType)
                .assetCode(assetCode)
                .active(true)
                .build();
    }

    public boolean matches(MarketType type, String code) {
        return this.marketType == type && this.assetCode.equalsIgnoreCase(code);
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }
}
