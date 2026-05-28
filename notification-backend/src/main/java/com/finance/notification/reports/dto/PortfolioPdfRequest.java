package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request for a portfolio PDF report, capturing the target portfolio plus theme/locale/currency. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioPdfRequest(
        @NotNull Long portfolioId,
        String portfolioName,
        @NotNull @Pattern(regexp = "LIGHT|DARK") String theme,
        @NotNull @Pattern(regexp = "tr|en") String locale,
        @NotNull @Pattern(regexp = "TRY|USD|EUR") String currency
) {}
