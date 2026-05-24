package com.finance.notification.reports.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioPdfRequest(
        @NotNull @Valid Portfolio portfolio,
        @NotNull @Valid Summary summary,
        @NotNull @Size(max = 500) @Valid List<Position> positions,
        @NotNull @Size(max = 50) @Valid List<AllocationSlice> allocation,
        @NotNull @Size(max = 5) Map<String, String> chartImages,
        @NotNull @Pattern(regexp = "TRY|USD|EUR") String currency,
        @NotNull ThemeVariant theme,
        @NotNull @Pattern(regexp = "tr|en") String locale
) {
    public record Portfolio(
            @NotNull Long id,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 200) String ownerEmail
    ) {}

    public record Summary(
            BigDecimal totalValue,
            BigDecimal totalCost,
            BigDecimal totalPnl,
            BigDecimal pnlPct,
            BigDecimal dailyPnl,
            BigDecimal dailyPnlPct
    ) {}

    public record Position(
            @NotBlank @Size(max = 32) String code,
            @Size(max = 120) String name,
            @NotBlank @Size(max = 16) String type,
            BigDecimal qty,
            BigDecimal entryPrice,
            BigDecimal currentPrice,
            BigDecimal marketValue,
            BigDecimal pnl,
            BigDecimal pnlPct
    ) {}

    public record AllocationSlice(
            @NotBlank @Size(max = 40) String label,
            @NotNull BigDecimal percent,
            @Size(max = 12) String color
    ) {}
}
