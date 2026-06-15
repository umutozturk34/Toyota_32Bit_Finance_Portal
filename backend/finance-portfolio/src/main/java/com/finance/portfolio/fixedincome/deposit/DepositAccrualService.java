package com.finance.portfolio.fixedincome.deposit;

import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.model.MoneyScale;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure-math accrual for a hypothetical {@link DepositHolding}, modelling a Türkiye time deposit (vadeli mevduat):
 * SIMPLE interest on the FROZEN {@code annualRate} (act/365), net of withholding tax (stopaj) which the bank
 * deducts from the interest at payout. Value = principal + interest × (1 − stopaj); the principal itself is never
 * taxed. Accrual caps at {@code maturityDate} (the value freezes after maturity) and accrues in the holding's own
 * {@code currency} — FX to TRY is the snapshot/response layer's job, deliberately not done here.
 */
@Service
public class DepositAccrualService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    // Largest value the numeric(23,8) money columns can hold (15 integer digits). The DepositValidator already
    // rejects any deposit whose projected maturity value would reach here; this is a last-resort clamp so an
    // unforeseen input can never make accrual emit a value that overflows the column on persist.
    private static final BigDecimal MAX_STORABLE = new BigDecimal("999999999999999.99999999");

    private final BigDecimal withholdingRate;

    public DepositAccrualService(PortfolioProperties portfolioProperties) {
        this.withholdingRate = portfolioProperties.getDeposit().getWithholdingTaxRate();
    }

    /** The configured DEFAULT withholding-tax (stopaj) rate, used when a deposit carries no per-holding override. */
    public BigDecimal withholdingRate() {
        return withholdingRate;
    }

    /** The stopaj rate actually applied to a holding: its own per-deposit rate when set, else the default. */
    public BigDecimal effectiveWithholdingRate(DepositHolding h) {
        return h != null && h.getWithholdingRate() != null ? h.getWithholdingRate() : withholdingRate;
    }

    /**
     * Full interest breakdown (in the holding's currency) at {@code asOf}: gross simple interest, the stopaj
     * withheld from it, the net interest, and the net realizable value (principal + net interest). Days are
     * counted from {@code startDate} to {@code min(asOf, maturityDate)} floored at 0, so an as-of before start
     * yields bare principal and one past maturity freezes at the maturity-day figure.
     */
    public DepositAccrual breakdown(DepositHolding h, LocalDate asOf) {
        LocalDate cappedAsOf = asOf.isAfter(h.getMaturityDate()) ? h.getMaturityDate() : asOf;
        long days = Math.max(0L, ChronoUnit.DAYS.between(h.getStartDate(), cappedAsOf));

        BigDecimal grossInterest = h.getPrincipal()
                .multiply(h.getAnnualRate().divide(HUNDRED, MathContext.DECIMAL64), MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, MathContext.DECIMAL64), MathContext.DECIMAL64);
        BigDecimal withholdingTax = grossInterest.multiply(effectiveWithholdingRate(h), MathContext.DECIMAL64);
        BigDecimal netInterest = grossInterest.subtract(withholdingTax);
        BigDecimal netValue = clamp(h.getPrincipal().add(netInterest));

        return new DepositAccrual(scale(grossInterest), scale(withholdingTax), scale(netInterest), scale(netValue));
    }

    /**
     * Net realizable value (in the holding's currency) of a still-running deposit at {@code asOf}: principal plus
     * net-of-stopaj interest. Convenience over {@link #breakdown} for callers that only need the headline value.
     */
    public BigDecimal accruedValue(DepositHolding h, LocalDate asOf) {
        return breakdown(h, asOf).netValue();
    }

    /**
     * Realized value for a closed deposit (the frozen {@code closedValueTry}, already net of stopaj at close),
     * else the live net accrued value. The closed value is the realized figure recorded at close, returned verbatim.
     */
    public BigDecimal realizedOrAccruedValue(DepositHolding h, LocalDate asOf) {
        return h.isActive() ? accruedValue(h, asOf) : h.getClosedValueTry();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.compareTo(MAX_STORABLE) > 0 ? MAX_STORABLE : value;
    }

    /**
     * Interest breakdown in the holding's currency: gross simple interest, the stopaj withheld from it, the net
     * interest after stopaj, and the net realizable value (principal + net interest).
     */
    public record DepositAccrual(BigDecimal grossInterest, BigDecimal withholdingTax,
                                 BigDecimal netInterest, BigDecimal netValue) {
    }
}
