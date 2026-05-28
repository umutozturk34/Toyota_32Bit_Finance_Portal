package com.finance.market.macro.model;

import com.finance.common.model.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A tracked macro/economic indicator (inflation, rate, deposit) defined by config and keyed by EVDS
 * code, caching its last observed value/date for staleness checks and quick display.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "macro_indicators",
        indexes = {
                @Index(name = "idx_macro_indicators_category", columnList = "category"),
                @Index(name = "idx_macro_indicators_prominent", columnList = "prominent")
        })
public class MacroIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, unique = true)
    private Instrument instrument;

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private MacroCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 16)
    private MacroUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private MacroFrequency frequency;

    @Column(name = "currency", length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "maturity", length = 16)
    private DepositMaturity maturity;

    @Column(name = "prominent", nullable = false)
    private boolean prominent;

    @Column(name = "last_value", precision = 19, scale = 6)
    private BigDecimal lastValue;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private MacroIndicator(Instrument instrument, String code, String label,
                          MacroCategory category, MacroUnit unit, MacroFrequency frequency,
                          String currency, DepositMaturity maturity, boolean prominent) {
        this.instrument = instrument;
        this.code = code;
        this.label = label;
        this.category = category;
        this.unit = unit;
        this.frequency = frequency;
        this.currency = currency;
        this.maturity = maturity;
        this.prominent = prominent;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Whether the latest point is older than the frequency allows (a new observation is overdue). */
    public boolean isStale(LocalDate today) {
        return frequency.isStale(lastDate, today);
    }

    /** Updates cached last value/date only when the observation is newer than the current latest. */
    public void recordObservation(LocalDate observedAt, BigDecimal value) {
        if (observedAt == null || value == null) {
            return;
        }
        if (lastDate == null || observedAt.isAfter(lastDate)) {
            this.lastDate = observedAt;
            this.lastValue = value;
        }
    }

    public void applyDefinition(String newLabel, MacroCategory newCategory, MacroUnit newUnit,
                                MacroFrequency newFrequency, String newCurrency,
                                DepositMaturity newMaturity, boolean nowProminent) {
        this.label = newLabel;
        this.category = newCategory;
        this.unit = newUnit;
        this.frequency = newFrequency;
        this.currency = newCurrency;
        this.maturity = newMaturity;
        this.prominent = nowProminent;
    }
}
