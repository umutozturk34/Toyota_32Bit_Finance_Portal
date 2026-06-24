package com.finance.portfolio.fixedincome.deposit;

import com.finance.common.exception.BusinessException;
import com.finance.portfolio.dto.request.DepositHoldingRequest;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Stateless guards for deposit commands. Mirrors {@code PortfolioValidator}: each rule throws a localized
 * {@link BusinessException} (the message is an i18n key, args fill its placeholders) so a violation surfaces
 * as a 422 with a translated message rather than a generic 400. Bounds intentionally repeat the JSR-380
 * caps on the request as a defence-in-depth layer that also carries domain-specific keys.
 */
@Log4j2
final class DepositValidator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("TRY", "USD", "EUR");
    private static final BigDecimal MIN_PRINCIPAL = new BigDecimal("0.01");
    // Principal cap (in the deposit's own currency). Generous for any realistic deposit, yet the real overflow
    // protection is the projected-maturity guard below: principal ALONE fitting the column is not enough because
    // it then compounds for the term and is FX-converted to TRY at close, both of which inflate the stored value.
    private static final BigDecimal MAX_PRINCIPAL = new BigDecimal("10000000000000");
    private static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    // 500% nominal annual: well above any real deposit rate (even Turkish hyperinflation-era deposits stayed
    // under ~100%) while leaving the projected-value guard a tractable exponent to bound. The old 1000% cap, at
    // 30y daily compounding, produced an astronomically large maturity value (~1e128) that overflowed the column.
    private static final BigDecimal MAX_RATE = new BigDecimal("500");
    // FX history floor shared with spot lots (LotLimits.minEntryDate): EUR/TRY rates start at the first 2000
    // trading day, so a deposit older than this could not be valued in TRY.
    private static final LocalDate MIN_START_DATE = LocalDate.of(2000, 1, 4);
    private static final int MAX_TERM_YEARS = 30;
    // The projected SIMPLE-interest maturity value (principal × (1 + rate/100 × days/365), in the deposit's own
    // currency) must stay under this ceiling so that — even after FX-conversion to TRY at close (USD/EUR rates
    // orbit ~40 TRY) — the realized value provably fits the numeric(23,8) money columns (max ~1e15). 1e13 native
    // × a generous 50× FX headroom = 5e14 < 1e15, so close()/persist can never throw a numeric-overflow DB error.
    private static final BigDecimal MAX_PROJECTED_VALUE = new BigDecimal("10000000000000");

    private DepositValidator() {
    }

    /** Runs every deposit guard against the resolved currency and the request's terms. */
    static void validate(String currency, DepositHoldingRequest request) {
        log.debug("Validating deposit: currency={} principal={} rate={} start={} maturity={}",
                currency, request.principal(), request.annualRate(), request.startDate(), request.maturityDate());
        validateCurrency(currency);
        validatePrincipal(request.principal());
        validateRate(request.annualRate());
        validateWithholding(request.withholdingRate());
        validateDates(request.startDate(), request.maturityDate());
        validateProjectedValue(request);
    }

    /**
     * Bounds the optional stopaj rate to a sane fraction [0, 1] (0%–100%). Null is allowed — the deposit then
     * falls back to the configured default withholding rate in {@code DepositAccrualService}.
     */
    private static void validateWithholding(BigDecimal rate) {
        if (rate == null) {
            return;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("error.portfolio.deposit.withholdingOutOfRange");
        }
    }

    private static void validateCurrency(String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
            throw new BusinessException("error.portfolio.deposit.currencyUnsupported", currency);
        }
    }

    private static void validatePrincipal(BigDecimal principal) {
        if (principal == null || principal.compareTo(MIN_PRINCIPAL) < 0) {
            throw new BusinessException("error.portfolio.deposit.principalTooLow", MIN_PRINCIPAL);
        }
        if (principal.compareTo(MAX_PRINCIPAL) > 0) {
            throw new BusinessException("error.portfolio.deposit.principalTooHigh", MAX_PRINCIPAL);
        }
    }

    private static void validateRate(BigDecimal annualRate) {
        if (annualRate == null || annualRate.compareTo(MIN_RATE) < 0 || annualRate.compareTo(MAX_RATE) > 0) {
            throw new BusinessException("error.portfolio.deposit.rateOutOfRange", MIN_RATE, MAX_RATE);
        }
    }

    /**
     * Rejects a deposit whose SIMPLE-interest value at maturity would exceed {@link #MAX_PROJECTED_VALUE}.
     * This is the real overflow guard: principal, rate and term may each be individually legal yet grow to
     * a value that — after FX-conversion to TRY at close — overflows the numeric(23,8) money columns, which
     * would surface only later as a DB error on close()/persist. Mirrors {@code DepositAccrualService}'s
     * act/365 SIMPLE interest so the projection matches what would actually be stored. Bails silently on any null
     * (the prior guards already rejected nulls; this avoids a redundant NPE if called in isolation).
     */
    private static void validateProjectedValue(DepositHoldingRequest request) {
        BigDecimal principal = request.principal();
        BigDecimal annualRate = request.annualRate();
        LocalDate startDate = request.startDate();
        LocalDate maturityDate = request.maturityDate();
        if (principal == null || annualRate == null || startDate == null || maturityDate == null) {
            return;
        }
        long days = Math.max(0L, ChronoUnit.DAYS.between(startDate, maturityDate));
        // Match DepositAccrualService's SIMPLE interest (act/365): value at maturity ≈ principal × (1 + rate% ×
        // days/365). The old daily-COMPOUND projection over-stated the maturity value enormously (e.g. ~1e128 at
        // 30y) and rejected high-rate/long-term deposits the service would actually value far lower.
        BigDecimal simpleGrowth = BigDecimal.ONE.add(
                annualRate.divide(HUNDRED, MathContext.DECIMAL64)
                        .multiply(BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, MathContext.DECIMAL64),
                                MathContext.DECIMAL64));
        BigDecimal projected = principal.multiply(simpleGrowth, MathContext.DECIMAL64);
        if (projected.compareTo(MAX_PROJECTED_VALUE) > 0) {
            throw new BusinessException("error.portfolio.deposit.projectedValueTooHigh", MAX_PROJECTED_VALUE);
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate maturityDate) {
        if (startDate == null || maturityDate == null) {
            // The JSR-380 @NotNull on the request already rejects nulls; guard here only so the strictly-after
            // and floor checks below never NPE if this validator is ever called directly.
            throw new BusinessException("error.portfolio.deposit.maturityBeforeStart");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.deposit.startDateInFuture");
        }
        if (startDate.isBefore(MIN_START_DATE)) {
            throw new BusinessException("error.portfolio.deposit.startDateTooOld", MIN_START_DATE);
        }
        if (!maturityDate.isAfter(startDate)) {
            throw new BusinessException("error.portfolio.deposit.maturityBeforeStart");
        }
        LocalDate maxMaturity = startDate.plusYears(MAX_TERM_YEARS);
        if (maturityDate.isAfter(maxMaturity)) {
            throw new BusinessException("error.portfolio.deposit.maturityTooFar", maxMaturity);
        }
    }
}
