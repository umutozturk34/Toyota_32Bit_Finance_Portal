package com.finance.backend.dto.response;

import java.util.List;

public record MarketOverviewResponse(
        List<MarketAssetResponse> indices,
        List<AssetTypeMovers> movers
) {

    public record AssetTypeMovers(
            String type,
            List<MarketAssetResponse> gainers,
            List<MarketAssetResponse> losers
    ) {}
}
