package com.finance.portfolio.fixedincome.bond;

import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.Portfolio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * A hypothetical Türkiye Hazine bond/bill (tahvil/bono) holding: {@code quantity} nominal of the bond
 * identified by {@code bondSeriesCode} (+ a denormalized {@code bondIsin}), bought at {@code entryPrice}
 * (TRY per 100 nominal) on {@code entryDate}, optionally exited. Bonds are ALWAYS TRY (never FX-converted).
 * Value comes from the clean price (per 100 nominal) via {@code BondValuationService}. Ownership flows through
 * {@link Portfolio} (portfolio_id -> portfolios.user_sub) — there is deliberately no user_sub column here.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_bond_holdings",
        indexes = @Index(name = "idx_bond_holdings_portfolio", columnList = "portfolio_id"))
public class BondHolding {

    /** Bond prices are quoted per 100 nominal, so value = price × quantity ÷ 100. */
    private static final BigDecimal PER_NOMINAL = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Column(name = "bond_series_code", nullable = false, length = 50)
    private String bondSeriesCode;

    @Column(name = "bond_isin", length = 50)
    private String bondIsin;

    @Column(name = "quantity", nullable = false, precision = 23, scale = 8)
    private BigDecimal quantity;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_price", precision = 19, scale = 4)
    private BigDecimal exitPrice;

    /**
     * User override for the coupon rate, interpreted as the ANNUAL coupon percent. The bond's published
     * {@code couponRate} (SEMI-ANNUAL) is only a suggestion; when the holder sets this it WINS and persists on
     * the holding. Null means "use the bond's published rate" (annualized as published × 2). Unlike v1, this now
     * FEEDS valuation: it drives the accrued-coupon (işlemiş kupon) component of the dirty value.
     */
    @Column(name = "coupon_rate_override", precision = 10, scale = 4)
    private BigDecimal couponRateOverride;

    /**
     * How often this holding pays its coupon, driving the accrued-coupon math (coupon dates step from the bond's
     * issue date by {@code 12 / paymentsPerYear} months). Defaults to {@link CouponFrequency#SEMI_ANNUAL}, the
     * Türkiye Hazine standard, so existing rows keep their prior behaviour.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_payment_frequency", nullable = false, length = 20)
    private CouponFrequency couponPaymentFrequency = CouponFrequency.SEMI_ANNUAL;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    /**
     * Cost basis in TRY = entry price × quantity. {@code quantity} is the number of bonds (adet) — one bond is one
     * lot of 100 nominal whose value IS the per-100 clean price — so a holding is valued PER UNIT for every type
     * (no ÷ 100). E.g. 1 bond at clean price 95 costs ₺95; a gold certificate at 6053 costs ₺6053.
     */
    public BigDecimal entryValue() {
        return entryPrice.multiply(quantity).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /** All bonds are now valued per unit; the {@code perUnit} flag is retained only for caller compatibility. */
    public BigDecimal entryValue(boolean perUnit) {
        return entryValue();
    }

    /**
     * Pull-to-par clean price for a zero-coupon discount bill (BONO). A discount bill pays no coupon — its
     * entire return is the price accreting from {@code entryPrice} (bought below par) to 100 (par redemption)
     * over the holding's life — so the as-of clean price is a straight-line interpolation between the entry
     * price on {@code entryDate} and par on {@code maturityEnd}. Before/at entry it is the entry price; on or
     * after maturity it is exactly par (100). This makes a bill bought at e.g. 75 deterministically return 100
     * at maturity instead of freezing at a stale scraped quote.
     */
    public BigDecimal accretedCleanPrice(LocalDate maturityEnd, LocalDate asOf) {
        if (maturityEnd == null || asOf == null || !asOf.isAfter(entryDate)) {
            return entryPrice;
        }
        if (!asOf.isBefore(maturityEnd)) {
            return PER_NOMINAL;
        }
        long total = ChronoUnit.DAYS.between(entryDate, maturityEnd);
        if (total <= 0) {
            return PER_NOMINAL;
        }
        long elapsed = ChronoUnit.DAYS.between(entryDate, asOf);
        BigDecimal fraction = BigDecimal.valueOf(elapsed)
                .divide(BigDecimal.valueOf(total), MathContext.DECIMAL64);
        BigDecimal accreted = entryPrice.add(PER_NOMINAL.subtract(entryPrice).multiply(fraction, MathContext.DECIMAL64));
        return accreted.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /**
     * Market value in TRY = clean price × quantity (number of bonds/adet) — PER UNIT for every type, since one
     * bond is one 100-nominal lot priced at the clean price. Zero when the price is unknown.
     */
    public BigDecimal currentValue(BigDecimal cleanPrice) {
        if (cleanPrice == null) return BigDecimal.ZERO;
        return cleanPrice.multiply(quantity).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /** All bonds are now valued per unit; the {@code perUnit} flag is retained only for caller compatibility. */
    public BigDecimal currentValue(BigDecimal cleanPrice, boolean perUnit) {
        return currentValue(cleanPrice);
    }

    /** True once the holding has been exited (an {@code exitDate} recorded). */
    public boolean isClosed() {
        return exitDate != null;
    }

    /** Records an exit at {@code price} (TRY per 100 nominal) on {@code when}. */
    public void closeWith(LocalDate when, BigDecimal price) {
        this.exitDate = when;
        this.exitPrice = price;
    }

    /** Clears the exit, returning the holding to open/held state. */
    public void reopen() {
        this.exitDate = null;
        this.exitPrice = null;
    }

    /**
     * Mutates editable attributes in place. Null quantity/price/date arguments leave the corresponding field
     * unchanged. The coupon override is handled differently: it is set unconditionally so that passing null
     * CLEARS a previously stored override (reverting to the bond's published rate), which a null-skip guard
     * could never express.
     */
    public void update(BigDecimal newQuantity, BigDecimal newEntryPrice, LocalDate newEntryDate,
                       BigDecimal newCouponRateOverride, CouponFrequency newCouponPaymentFrequency) {
        if (newQuantity != null) this.quantity = newQuantity;
        if (newEntryPrice != null) this.entryPrice = newEntryPrice;
        if (newEntryDate != null) this.entryDate = newEntryDate;
        this.couponRateOverride = newCouponRateOverride;
        if (newCouponPaymentFrequency != null) this.couponPaymentFrequency = newCouponPaymentFrequency;
    }

    /**
     * The effective ANNUAL coupon rate to value/display for this holding: the user's {@code couponRateOverride}
     * (already annual) when set, otherwise the bond's published PER-PERIOD coupon annualized by this holding's
     * payment frequency ({@code published × paymentsPerYear}). Treating the published rate as the per-period coupon
     * keeps {@code couponPerPeriod == published} at ANY frequency — for the Türkiye Hazine semi-annual standard
     * this is the familiar × 2 (EVDS {@code .ORAN}), and a quarterly TLREF floater annualizes × 4 consistently
     * instead of being silently halved. Null when no rate is available or the frequency pays no coupon.
     */
    public BigDecimal effectiveAnnualCouponRate(BigDecimal publishedPerPeriodRate) {
        if (couponRateOverride != null) {
            return couponRateOverride;
        }
        if (publishedPerPeriodRate == null || couponPaymentFrequency == null
                || !couponPaymentFrequency.paysCoupon()) {
            return null;
        }
        return publishedPerPeriodRate.multiply(BigDecimal.valueOf(couponPaymentFrequency.paymentsPerYear()));
    }
}
