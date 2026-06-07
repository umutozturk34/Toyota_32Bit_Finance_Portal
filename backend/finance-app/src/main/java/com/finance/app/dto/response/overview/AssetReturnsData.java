package com.finance.app.dto.response.overview;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ASSET_RETURNS widget payload: the selected window, the widget's OWN display currency, the dataset's as-of
 * date, the active sort key, and the ranked rows (one spot asset each) already filtered/sorted/limited to the
 * section's config. Figures are expressed in {@code currency} (TRY/USD/EUR — a per-widget setting), each leg
 * converted at its own date's FX upstream, so this widget's ranking is independent of the others.
 */
public record AssetReturnsData(
        String period,
        String currency,
        LocalDate asOf,
        String sortBy,
        List<ReturnRow> entries
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.ASSET_RETURNS;
    }

    public record ReturnRow(
            String type,
            String code,
            String name,
            BigDecimal returnPct,
            BigDecimal returnTry,
            BigDecimal priceNow,
            BigDecimal volatility,
            String riskLevel
    ) {
    }
}
