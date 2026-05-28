package com.finance.market.macro.model;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A single observed value of a macro indicator on a date; unique per (indicator, date). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "macro_indicator_points",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_macro_points_indicator_date",
                        columnNames = {"indicator_id", "observed_at"})
        },
        indexes = {
                @Index(name = "idx_macro_points_indicator_date",
                        columnList = "indicator_id, observed_at DESC")
        })
public class MacroIndicatorPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private MacroIndicator indicator;

    @Column(name = "observed_at", nullable = false)
    private LocalDate observedAt;

    @Column(name = "value", nullable = false, precision = 19, scale = 6)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private MacroIndicatorPoint(MacroIndicator indicator, LocalDate observedAt, BigDecimal value) {
        this.indicator = indicator;
        this.observedAt = observedAt;
        this.value = value;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
