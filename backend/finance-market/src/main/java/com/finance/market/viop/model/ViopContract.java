package com.finance.market.viop.model;

import com.finance.market.core.model.BaseAsset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "viop_contracts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uc_viop_contracts_symbol", columnNames = {"symbol"})
        },
        indexes = {
                @Index(name = "idx_viop_contracts_kind", columnList = "kind"),
                @Index(name = "idx_viop_contracts_category", columnList = "category"),
                @Index(name = "idx_viop_contracts_active_expiry", columnList = "active,expiry_date"),
                @Index(name = "idx_viop_contracts_underlying", columnList = "underlying")
        })
public class ViopContract extends BaseAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "symbol", nullable = false, length = 64)
    private String symbol;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private ViopContractKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32)
    private ViopCategory category;

    @Column(name = "underlying", length = 64)
    private String underlying;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "contract_size", precision = 19, scale = 8)
    private BigDecimal contractSize;

    @Column(name = "initial_margin", precision = 19, scale = 4)
    private BigDecimal initialMargin;

    @Column(name = "settlement_type", length = 32)
    private String settlementType;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "tick_size", precision = 19, scale = 8)
    private BigDecimal tickSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_side", length = 8)
    private ViopOptionSide optionSide;

    @Column(name = "strike_price", precision = 19, scale = 4)
    private BigDecimal strikePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_style", length = 16)
    private ViopExerciseStyle exerciseStyle;

    @Column(name = "last_price", precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "day_close", precision = 19, scale = 4)
    private BigDecimal dayClose;

    @Column(name = "bid", precision = 19, scale = 4)
    private BigDecimal bid;

    @Column(name = "ask", precision = 19, scale = 4)
    private BigDecimal ask;

    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "day_high", precision = 19, scale = 4)
    private BigDecimal dayHigh;

    @Column(name = "day_low", precision = 19, scale = 4)
    private BigDecimal dayLow;

    @Column(name = "volume_lot", precision = 19, scale = 4)
    private BigDecimal volumeLot;

    @Column(name = "volume_try", precision = 19, scale = 4)
    private BigDecimal volumeTry;

    @Column(name = "settlement_price", precision = 19, scale = 4)
    private BigDecimal settlementPrice;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Override
    public void scaleFields(int scale) {
        this.lastPrice = scaleValue(this.lastPrice, scale);
        this.dayClose = scaleValue(this.dayClose, scale);
        this.bid = scaleValue(this.bid, scale);
        this.ask = scaleValue(this.ask, scale);
        this.openPrice = scaleValue(this.openPrice, scale);
        this.dayHigh = scaleValue(this.dayHigh, scale);
        this.dayLow = scaleValue(this.dayLow, scale);
        this.volumeLot = scaleValue(this.volumeLot, scale);
        this.volumeTry = scaleValue(this.volumeTry, scale);
        this.settlementPrice = scaleValue(this.settlementPrice, scale);
        this.strikePrice = scaleValue(this.strikePrice, scale);
        setChangeAmount(scaleValue(getChangeAmount(), scale));
        setChangePercent(scaleValue(getChangePercent(), scale));
    }

    @Override
    public String getCode() {
        return symbol;
    }

    @Override
    public BigDecimal getPriceTry() {
        return lastPrice != null ? lastPrice : dayClose;
    }

    @Override
    public String resolvePriceCurrency() {
        return currency != null && !currency.isBlank() ? currency : "TRY";
    }

    @Override
    public String resolveDisplayName() {
        return firstNonBlank(displayName, getName(), symbol);
    }
}
