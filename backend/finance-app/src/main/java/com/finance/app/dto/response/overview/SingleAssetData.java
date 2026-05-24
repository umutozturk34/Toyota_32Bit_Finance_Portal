package com.finance.app.dto.response.overview;

import com.finance.market.core.dto.response.MarketAssetResponse;

public record SingleAssetData(MarketAssetResponse asset) implements WidgetData {

    @Override
    public WidgetKind kind() {
        return WidgetKind.SINGLE_ASSET;
    }
}
