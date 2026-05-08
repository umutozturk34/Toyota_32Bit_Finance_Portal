package com.finance.app.dto.response.overview;

import com.finance.market.core.dto.response.MarketAssetResponse;

import java.util.List;

public record AssetCardsData(List<MarketAssetResponse> items) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.ASSET_CARDS;
    }
}
