package com.finance.portfolio.fixedincome.deposit;

import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.Portfolio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A hypothetical DEPOSIT (mevduat) holding: {@code principal} held in {@code currency}, earning a FROZEN
 * {@code annualRate} (SIMPLE interest, act/365, net of stopaj — see {@code DepositAccrualService}) from {@code startDate} to
 * {@code maturityDate}, after which the value freezes. The rate is frozen at create so the return is
 * deterministic (matching the rest of the hypothetical-lot portfolio); the macro deposit series only
 * prefills it in the form. Ownership flows through {@link Portfolio} (portfolio_id -> portfolios.user_sub) —
 * there is deliberately no user_sub column here.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "portfolio_deposit_holdings",
        indexes = @Index(name = "idx_deposit_holdings_portfolio", columnList = "portfolio_id"))
public class DepositHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "portfolio_id", insertable = false, updatable = false)
    private Long portfolioId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "principal", nullable = false, precision = 23, scale = 8)
    private BigDecimal principal;

    @Column(name = "annual_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal annualRate;

    /** EVDS macro-deposit indicator code the rate was prefilled from (reference only; the rate stays frozen). */
    @Column(name = "indicator_code", length = 64)
    private String indicatorCode;

    /**
     * Withholding-tax (stopaj) rate applied to this deposit's interest, as a FRACTION (e.g. {@code 0.1500} for
     * 15%). Türkiye deposit stopaj varies by term and government decree, so the holder enters it per deposit;
     * {@code null} falls back to the configured default in {@code DepositAccrualService}.
     */
    @Column(name = "withholding_rate", precision = 6, scale = 4)
    private BigDecimal withholdingRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "closed_value_try", precision = 23, scale = 8)
    private BigDecimal closedValueTry;

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
        if (currency == null) currency = "TRY";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** True while the deposit is still running (not manually closed). */
    public boolean isActive() {
        return closedDate == null;
    }

    /** Records a manual close at {@code when}, freezing the accrued {@code valueTry} as the realized value. */
    public void close(LocalDate when, BigDecimal valueTry) {
        this.closedDate = when;
        this.closedValueTry = valueTry == null ? null
                : valueTry.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /** Re-opens a closed deposit, clearing the frozen realized value. */
    public void reopen() {
        this.closedDate = null;
        this.closedValueTry = null;
    }

    /**
     * Mutates editable attributes in place; null principal/rate/date/currency/indicator arguments leave the
     * corresponding field unchanged. The withholding rate is set UNCONDITIONALLY so passing null reverts the
     * deposit to the configured default stopaj (a null-skip could never express that).
     */
    public void update(BigDecimal newPrincipal, BigDecimal newAnnualRate, LocalDate newStartDate,
                       LocalDate newMaturityDate, String newCurrency, String newIndicatorCode,
                       BigDecimal newWithholdingRate) {
        if (newPrincipal != null) this.principal = newPrincipal;
        if (newAnnualRate != null) this.annualRate = newAnnualRate;
        if (newStartDate != null) this.startDate = newStartDate;
        if (newMaturityDate != null) this.maturityDate = newMaturityDate;
        if (newCurrency != null) this.currency = newCurrency;
        if (newIndicatorCode != null) this.indicatorCode = newIndicatorCode;
        this.withholdingRate = newWithholdingRate;
    }
}
