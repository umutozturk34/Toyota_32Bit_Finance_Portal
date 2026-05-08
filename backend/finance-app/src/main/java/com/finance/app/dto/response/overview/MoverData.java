package com.finance.app.dto.response.overview;

import com.finance.common.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;

import java.util.List;

public record MoverData(
        MarketType market,
        List<MarketAssetResponse> gainers,
        List<MarketAssetResponse> losers
) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.MOVERS;
    }
}
