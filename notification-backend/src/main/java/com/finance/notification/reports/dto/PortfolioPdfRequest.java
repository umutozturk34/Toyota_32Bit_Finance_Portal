package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request for a portfolio PDF report, capturing the target portfolio plus theme/locale/currency.
 *
 * <p>The report supports {@code TRY}, {@code USD} and {@code EUR}. Portfolio figures are stored and
 * computed in Turkish lira; for a non-TRY currency every monetary value is converted from lira at
 * that value's own historical daily FX rate (forward-filled across weekends/holidays), so the
 * printed amounts match the on-screen charts rather than applying a single spot rate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioPdfRequest(
        @NotNull Long portfolioId,
        @Size(max = 200) String portfolioName,
        @NotNull @Pattern(regexp = "LIGHT|DARK") String theme,
        @NotNull @Pattern(regexp = "tr|en") String locale,
        @NotNull @Pattern(regexp = "TRY|USD|EUR") String currency
) {}
