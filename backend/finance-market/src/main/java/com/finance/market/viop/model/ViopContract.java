package com.finance.market.viop.model;

import com.finance.common.model.Currency;
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

/**
 * A VIOP (Turkish derivatives exchange) futures or options contract with its live quote and
 * day stats. The {@code symbol} encodes underlying, expiry and (for options) strike/side, and is
 * the source of truth for the quote currency: the stored {@code currency} (exchange PARA_BIRIMI)
 * is the underlying currency and must not be used for FX.
 */
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

    /**
     * Rescales every monetary/price field plus the derived change amount and percent to the given
     * decimal scale in place. {@code strikePrice} is included (relevant for options); contract
     * metadata such as size, margin and tick size is left untouched.
     *
     * @param scale target decimal scale to apply
     */
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

    /** Asset code used by the {@link BaseAsset} contract; for a VIOP contract this is its {@code symbol}. */
    @Override
    public String getCode() {
        return symbol;
    }

    /** Price in quote currency: last traded price, falling back to the day's close. */
    @Override
    public BigDecimal getPriceTry() {
        return lastPrice != null ? lastPrice : dayClose;
    }

    /** Quote currency derived from the symbol (not the stored exchange currency). */
    @Override
    public String resolvePriceCurrency() {
        return quoteCurrencyOf(symbol);
    }

    /**
     * Quote currency a VIOP contract's price is denominated in, derived from the symbol.
     * Symbols carry a trailing expiry (futures: MMYY) or strike (options); the pair's quote
     * currency is the last currency token before that. e.g. F_USDTRY0625 -> TRY (price is TRY
     * per USD), F_EURUSD0625 -> USD, stock/index futures and options -> TRY.
     * The stored {@code currency} field (exchange PARA_BIRIMI) is the underlying currency, not
     * the quote currency, so it must not be used for FX conversion.
     */
    public static String quoteCurrencyOf(String symbol) {
        return Currency.viopQuoteCurrencyOf(symbol).name();
    }

    /** Human-readable label, preferring the explicit display name, then the base name, then the raw symbol. */
    @Override
    public String resolveDisplayName() {
        return firstNonBlank(displayName, getName(), symbol);
    }
}
