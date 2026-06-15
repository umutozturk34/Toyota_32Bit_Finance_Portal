package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to add or update a hypothetical DEPOSIT (mevduat) holding. {@code currency} is the deposit's
 * settlement currency (TRY/USD/EUR; null defaults to TRY); {@code annualRate} is the FROZEN nominal yearly
 * rate in percent (e.g. {@code 45.00} for 45%). The optional {@code indicatorCode} is the EVDS macro-deposit
 * series the rate was prefilled from and is reference-only — the persisted rate stays frozen regardless.
 *
 * <p>JSR-380 here only enforces shape/bound sanity that maps to the persisted columns; the semantic guards
 * (currency whitelist, date ordering/floors, principal/rate caps) live in {@code DepositValidator} so they
 * throw localized business errors rather than a generic 400.
 */
public record DepositHoldingRequest(
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("10000000000000")
        @Digits(integer = 15, fraction = 8) BigDecimal principal,
        @NotNull @PositiveOrZero @DecimalMax("1000") @Digits(integer = 6, fraction = 4) BigDecimal annualRate,
        @Size(max = 64) String indicatorCode,
        @NotNull LocalDate startDate,
        @NotNull LocalDate maturityDate,
        // Withholding-tax (stopaj) rate as a FRACTION (e.g. 0.15 for 15%); optional — null uses the configured
        // default. Türkiye deposit stopaj varies by term/decree, so the holder may set it per deposit.
        @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal withholdingRate
) {

    /** Convenience for callers/tests that don't prefill from a macro series and use the default stopaj. */
    public DepositHoldingRequest(String currency, BigDecimal principal, BigDecimal annualRate,
                                 LocalDate startDate, LocalDate maturityDate) {
        this(currency, principal, annualRate, null, startDate, maturityDate, null);
    }

    /** Convenience preserving the prior canonical arity (indicator code, default stopaj). */
    public DepositHoldingRequest(String currency, BigDecimal principal, BigDecimal annualRate,
                                 String indicatorCode, LocalDate startDate, LocalDate maturityDate) {
        this(currency, principal, annualRate, indicatorCode, startDate, maturityDate, null);
    }

    /** The currency to persist, defaulting a null/blank request currency to TRY. */
    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "TRY" : currency;
    }
}
