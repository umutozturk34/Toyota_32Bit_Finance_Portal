package com.finance.notification.reports.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record PortfolioPdfModel(
        PortfolioPdfRequest.Portfolio portfolio,
        PortfolioPdfRequest.Summary summary,
        List<PortfolioPdfRequest.Position> positions,
        List<PortfolioPdfRequest.AllocationSlice> allocation,
        Map<String, String> chartImages,
        String currency,
        ThemeVariant theme,
        Locale locale,
        LocalDateTime generatedAt
) {
    public static PortfolioPdfModel fromRequest(PortfolioPdfRequest req, LocalDateTime now) {
        return new PortfolioPdfModel(
                req.portfolio(), req.summary(), req.positions(), req.allocation(),
                req.chartImages(), req.currency(), req.theme(),
                Locale.forLanguageTag(req.locale()), now);
    }
}
