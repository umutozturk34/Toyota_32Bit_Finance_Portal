package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.MarketOverviewResponse;
import com.finance.backend.model.MarketType;
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
            if (gainers.getOrDefault(type, List.of()).isEmpty()) {
                gainers.put(type, provider.getTopMovers(limit, true));
            }
            if (losers.getOrDefault(type, List.of()).isEmpty()) {
                losers.put(type, provider.getTopMovers(limit, false));
            }
        }

        List<MarketAssetResponse> indices = topMoversRedisService.getIndices();
        if (indices.isEmpty()) {
            indices = providers.values().stream()
                    .flatMap(p -> p.getIndices().stream())
                    .toList();
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
}
