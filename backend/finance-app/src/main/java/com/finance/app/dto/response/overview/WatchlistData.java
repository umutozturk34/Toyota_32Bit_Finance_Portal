package com.finance.app.dto.response.overview;

import com.finance.common.model.MarketType;

import java.math.BigDecimal;
import java.util.List;

/** WATCHLIST widget payload: the resolved watchlist and its items enriched with live price/change. */
public record WatchlistData(
        Long watchlistId,
        String watchlistName,
        List<WatchlistRow> items
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.WATCHLIST;
    }

    public record WatchlistRow(
            String assetCode,
            MarketType marketType,
            String image,
            BigDecimal price,
            BigDecimal changePercent,
            String currency
    ) {
    }
}
