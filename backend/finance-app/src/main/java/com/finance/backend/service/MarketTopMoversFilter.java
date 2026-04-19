package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.StockMetadata;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.StockSegment;

import java.util.List;

public final class MarketTopMoversFilter {

    private MarketTopMoversFilter() {
    }

    public static List<MarketAssetResponse> apply(MarketType type, List<MarketAssetResponse> items) {
        if (type != MarketType.STOCK) return items;
        return items.stream()
                .filter(a -> !(a.metadata() instanceof StockMetadata sm) || sm.stockSegment() != StockSegment.MAIN_INDEX)
                .toList();
    }
}
