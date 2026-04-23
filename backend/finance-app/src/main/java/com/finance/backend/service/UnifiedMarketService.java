package com.finance.backend.service;
import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.model.MarketType;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class UnifiedMarketService implements MarketUpdatePort {

    private final Map<MarketType, MarketAssetProvider> providers;
    private final Map<MarketType, MarketHistoryProvider> historyProviders;
    private final TopMoversRedisService topMoversRedisService;

    public UnifiedMarketService(List<MarketAssetProvider> providerList,
                                List<MarketHistoryProvider> historyProviderList,
                                TopMoversRedisService topMoversRedisService) {
        this.providers = new EnumMap<>(MarketType.class);
        providerList.forEach(p -> this.providers.put(p.getType(), p));
        this.historyProviders = new EnumMap<>(MarketType.class);
        historyProviderList.forEach(p -> this.historyProviders.put(p.getMarketType(), p));
        this.topMoversRedisService = topMoversRedisService;
    }

    public PagedResponse<MarketAssetResponse> search(List<MarketType> types, String code,
                                                     String segment, String subType, String search,
                                                     String sort, String direction,
                                                     String filter, int page, int size) {
        if (code != null && !code.isBlank()) {
            return handleSingleAssetLookup(types, code);
        }

        boolean hasSearch = search != null && !search.isBlank();

        MarketAssetProvider.MarketAssetFilters filters = new MarketAssetProvider.MarketAssetFilters(segment, subType);
        List<MarketAssetResponse> allResults = new ArrayList<>();
        long total = 0;
        for (MarketType type : types) {
            MarketAssetProvider provider = providers.get(type);
            if (provider == null) continue;
            allResults.addAll(provider.search(search, filters, sort, direction, page, size));
            total += hasSearch ? provider.countBySearch(search, filters) : provider.count(filters);
        }

        List<MarketAssetResponse> filtered = applyFilter(allResults, filter);
        return PagedResponse.of(applySort(filtered, sort, direction), page, size, total);
    }

    public List<GroupCount> getGroupCounts(MarketType type) {
        MarketAssetProvider provider = providers.get(type);
        if (provider == null) return List.of();
        return provider.getGroupCounts();
    }

    public List<?> getHistory(MarketType type, String code, CandlePeriod period) {
        MarketHistoryProvider provider = historyProviders.get(type);
        if (provider == null) {
            throw new ResourceNotFoundException("No history provider registered for " + type);
        }
        return provider.getHistory(code, period);
    }

    @Override
    public void onMarketDataUpdated(MarketType type) {
        MarketAssetProvider provider = providers.get(type);
        if (provider == null) return;

        try {
            topMoversRedisService.updateGainers(type, provider.getTopMovers(10, true));
            topMoversRedisService.updateLosers(type, provider.getTopMovers(10, false));

            List<MarketAssetResponse> indices = provider.getIndices();
            if (!indices.isEmpty()) {
                topMoversRedisService.updateIndices(indices);
            }

            log.debug("Write-through cache updated for {}", type);
        } catch (Exception e) {
            log.warn("Failed to update write-through cache for {}: {}", type, e.getMessage());
        }
    }

    private PagedResponse<MarketAssetResponse> handleSingleAssetLookup(List<MarketType> types, String code) {
        for (MarketType type : types) {
            MarketAssetProvider provider = providers.get(type);
            if (provider == null) continue;
            try {
                MarketAssetResponse found = provider.getByCode(code);
                if (found != null) {
                    return PagedResponse.of(List.of(found), 0, 1, 1);
                }
            } catch (Exception e) {
                log.debug("Asset lookup failed for type={} code={}: {}", type, code, e.getMessage());
            }
        }
        throw new ResourceNotFoundException("Asset not found: " + code);
    }

    private List<MarketAssetResponse> applyFilter(List<MarketAssetResponse> items, String filter) {
        if (filter == null || "all".equalsIgnoreCase(filter)) return items;
        return items.stream()
                .filter(a -> a.changePercent() != null)
                .filter(a -> "gainers".equalsIgnoreCase(filter)
                        ? a.changePercent().signum() > 0
                        : a.changePercent().signum() < 0)
                .toList();
    }

    private List<MarketAssetResponse> applySort(List<MarketAssetResponse> items,
                                                String sort, String direction) {
        if (sort == null || sort.isBlank()) return items;
        Comparator<MarketAssetResponse> comparator = switch (sort) {
            case "price" -> Comparator.comparing(MarketAssetResponse::price,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "name" -> Comparator.comparing(MarketAssetResponse::name,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> Comparator.comparing(MarketAssetResponse::changePercent,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if ("asc".equalsIgnoreCase(direction)) {
            return items.stream().sorted(comparator).toList();
        }
        return items.stream().sorted(comparator.reversed()).toList();
    }
}
