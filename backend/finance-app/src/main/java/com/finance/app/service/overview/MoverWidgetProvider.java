package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.app.dto.response.overview.MoverData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.market.core.cache.TopMoversRedisService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.common.util.EnumDispatcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class MoverWidgetProvider implements OverviewWidgetProvider {

    private final Map<MarketType, MarketAssetProvider> providersByType;
    private final TopMoversRedisService topMoversCache;
    private final OverviewDefaults defaults;

    public MoverWidgetProvider(List<MarketAssetProvider> providers,
                               TopMoversRedisService topMoversCache,
                               OverviewDefaults defaults) {
        this.providersByType = EnumDispatcher.from(MarketType.class, providers, MarketAssetProvider::getType);
        this.topMoversCache = topMoversCache;
        this.defaults = defaults;
    }

    @Override
    public WidgetKind kind() {
        return WidgetKind.MOVERS;
    }

    @Override
    public MoverData fetch(String userSub, WidgetSection section) {
        MarketType market = readMarket(section);
        if (market == null) return new MoverData(null, List.of(), List.of());
        int limit = readLimit(section);
        List<MarketAssetResponse> gainers = resolveGainers(market, limit);
        List<MarketAssetResponse> losers = resolveLosers(market, limit);
        return new MoverData(market, gainers, losers);
    }

    private MarketType readMarket(WidgetSection section) {
        JsonNode node = section.config().get("market");
        if (node == null || node.isNull()) return null;
        try {
            return MarketType.valueOf(node.asText());
        } catch (IllegalArgumentException ex) {
            log.debug("MoverWidget skip — invalid market value={}", node.asText());
            return null;
        }
    }

    private int readLimit(WidgetSection section) {
        JsonNode node = section.config().get("limit");
        if (node == null || !node.isInt() || node.asInt() <= 0) return defaults.defaultMoverLimit();
        return Math.min(node.asInt(), defaults.maxConfigLimit());
    }

    private List<MarketAssetResponse> resolveGainers(MarketType market, int limit) {
        List<MarketAssetResponse> cached = topMoversCache.getGainers(market);
        if (!cached.isEmpty()) return capped(cached, limit);
        return capped(callProvider(market, limit, true), limit);
    }

    private List<MarketAssetResponse> resolveLosers(MarketType market, int limit) {
        List<MarketAssetResponse> cached = topMoversCache.getLosers(market);
        if (!cached.isEmpty()) return capped(cached, limit);
        return capped(callProvider(market, limit, false), limit);
    }

    private List<MarketAssetResponse> callProvider(MarketType market, int limit, boolean gainers) {
        MarketAssetProvider provider = providersByType.get(market);
        if (provider == null) return List.of();
        return provider.getTopMovers(limit, gainers);
    }

    private List<MarketAssetResponse> capped(List<MarketAssetResponse> source, int limit) {
        if (source.size() <= limit) return source;
        return source.subList(0, limit);
    }
}
