package com.finance.portfolio.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A deposit holding row for the UI: identity + frozen terms, plus its valuation expressed in TRY. For a
 * still-running deposit {@code currentValueTry} is the live NET (after-stopaj) accrued value FX-converted to TRY
 * at today's rate (a TRY deposit needs no conversion); for a closed deposit it is the frozen realized value
 * recorded at close. {@code pnlTry} is value − principalTry and {@code pnlPercent} the same as a percentage of the
 * TRY principal.
 *
 * <p>The interest breakdown ({@code grossInterestTry}, {@code withholdingTaxTry}, {@code netInterestTry}, with the
 * applied {@code withholdingRate}) decomposes the deposit's accrued interest so the UI can show gross interest, the
 * stopaj withheld, and the net the holder actually receives. All TRY-converted at the value date.
 */
public record DepositHoldingResponse(
        Long id,
        String currency,
        BigDecimal principal,
        BigDecimal annualRate,
        String indicatorCode,
        LocalDate startDate,
        LocalDate maturityDate,
        LocalDate closedDate,
        boolean active,
        BigDecimal currentValueTry,
        BigDecimal pnlTry,
        BigDecimal pnlPercent,
        BigDecimal grossInterestTry,
        BigDecimal withholdingTaxTry,
        BigDecimal netInterestTry,
        BigDecimal withholdingRate
) {}
