package com.finance.app.dto.response.overview;

import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;

import java.util.List;

/** MOVERS widget payload: top gainers and losers for a market type. */
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
