package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioPdfRequest(
        @NotNull Long portfolioId,
        @NotNull @Pattern(regexp = "LIGHT|DARK") String theme,
        @NotNull @Pattern(regexp = "tr|en") String locale,
        @NotNull @Pattern(regexp = "TRY|USD|EUR") String currency
) {}
