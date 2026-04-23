package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.MarketOverviewResponse;
import com.finance.backend.dto.response.StockMetadata;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.StockSegment;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class MarketOverviewService {

    private final Map<MarketType, MarketAssetProvider> providers;
    private final TopMoversRedisService topMoversRedisService;

    public MarketOverviewService(List<MarketAssetProvider> providerList,
                                 TopMoversRedisService topMoversRedisService) {
        this.providers = new EnumMap<>(MarketType.class);
        providerList.forEach(p -> this.providers.put(p.getType(), p));
        this.topMoversRedisService = topMoversRedisService;
    }

    public MarketOverviewResponse getOverview(int limit) {
        Map<MarketType, List<MarketAssetResponse>> gainers = topMoversRedisService.getAllGainers();
        Map<MarketType, List<MarketAssetResponse>> losers = topMoversRedisService.getAllLosers();

        for (MarketType type : MarketType.values()) {
            MarketAssetProvider provider = providers.get(type);
            if (provider == null) continue;
            gainers.computeIfAbsent(type, t -> MarketTopMoversFilter.apply(t, provider.getTopMovers(limit, true)));
            losers.computeIfAbsent(type, t -> MarketTopMoversFilter.apply(t, provider.getTopMovers(limit, false)));
        }

        List<MarketAssetResponse> indices = topMoversRedisService.getIndices();
        if (indices.isEmpty()) {
            indices = loadIndicesFromStockProvider();
        }

        List<MarketOverviewResponse.AssetTypeMovers> movers = new ArrayList<>();
        for (MarketType type : MarketType.values()) {
            List<MarketAssetResponse> typeGainers = gainers.getOrDefault(type, List.of()).stream().limit(limit).toList();
            List<MarketAssetResponse> typeLosers = losers.getOrDefault(type, List.of()).stream().limit(limit).toList();
            if (!typeGainers.isEmpty() || !typeLosers.isEmpty()) {
                movers.add(new MarketOverviewResponse.AssetTypeMovers(type.name(), typeGainers, typeLosers));
            }
        }

        return new MarketOverviewResponse(indices, movers);
    }

    private List<MarketAssetResponse> loadIndicesFromStockProvider() {
        MarketAssetProvider stockProvider = providers.get(MarketType.STOCK);
        if (stockProvider == null) return List.of();
        return stockProvider.search(null,
                        MarketAssetProvider.MarketAssetFilters.ofSegment("MAIN_INDEX"),
                        "changePercent", "desc", 0, 100).stream()
                .filter(a -> a.metadata() instanceof StockMetadata sm && sm.stockSegment() == StockSegment.MAIN_INDEX)
                .toList();
    }
}
