package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.MarketOverviewResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.MarketType;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Log4j2
@Service
public class UnifiedMarketService implements MarketUpdatePort {

    private final Map<MarketType, MarketAssetProvider> providers;
    private final TopMoversRedisService topMoversRedisService;
    private final StockQueryService stockQueryService;
    private final CryptoQueryService cryptoQueryService;
    private final FundQueryService fundQueryService;
    private final ForexQueryService forexQueryService;

    public UnifiedMarketService(List<MarketAssetProvider> providerList,
                                TopMoversRedisService topMoversRedisService,
                                StockQueryService stockQueryService,
                                CryptoQueryService cryptoQueryService,
                                FundQueryService fundQueryService,
                                ForexQueryService forexQueryService) {
        this.providers = new EnumMap<>(MarketType.class);
        providerList.forEach(p -> this.providers.put(p.getType(), p));
        this.topMoversRedisService = topMoversRedisService;
        this.stockQueryService = stockQueryService;
        this.cryptoQueryService = cryptoQueryService;
        this.fundQueryService = fundQueryService;
        this.forexQueryService = forexQueryService;
    }

    public PagedResponse<MarketAssetResponse> search(List<MarketType> types, String code,
                                                     String segment, String subType, String search,
                                                     String sort, String direction,
                                                     String filter, int page, int size) {
        if (code != null && !code.isBlank()) {
            return handleSingleAssetLookup(types, code);
        }

        boolean hasSubType = subType != null && !subType.isBlank();
        boolean hasSegment = segment != null && !segment.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasFilter = filter != null && !"all".equalsIgnoreCase(filter);
        boolean hasSort = sort != null && !sort.isBlank();

        boolean useDbQuery = hasSort || hasSearch;

        List<MarketAssetResponse> allResults = new ArrayList<>();

        if (useDbQuery) {
            for (MarketType type : types) {
                MarketAssetProvider provider = providers.get(type);
                if (provider == null) continue;
                allResults.addAll(provider.search(search, resolveSort(sort), direction, page, size));
            }
            if (!hasSegment && !hasSubType && !hasFilter) {
                long total = 0;
                for (MarketType type : types) {
                    MarketAssetProvider provider = providers.get(type);
                    if (provider == null) continue;
                    total += hasSearch ? provider.countBySearch(search) : provider.count();
                }
                return PagedResponse.of(applySort(allResults, sort, direction), page, size, total);
            }
        } else {
            for (MarketType type : types) {
                MarketAssetProvider provider = providers.get(type);
                if (provider == null) continue;
                allResults.addAll(provider.getAll());
            }
        }

        if (hasSegment) {
            allResults = allResults.stream()
                    .filter(a -> a.metadata() != null && segment.equals(a.metadata().get("stockSegment")))
                    .toList();
        }

        if (hasSubType) {
            allResults = allResults.stream()
                    .filter(a -> a.metadata() != null && subType.equals(a.metadata().get("fundType")))
                    .toList();
        }

        List<MarketAssetResponse> filtered = applyFilter(allResults, filter);
        long totalElements = filtered.size();
        List<MarketAssetResponse> sorted = hasSort ? applySort(filtered, sort, direction) : filtered;
        List<MarketAssetResponse> paged = sorted.stream().skip((long) page * size).limit(size).toList();

        return PagedResponse.of(paged, page, size, totalElements);
    }

    public MarketOverviewResponse getOverview(int limit) {
        Map<MarketType, List<MarketAssetResponse>> cached = topMoversRedisService.getAllMovers();

        List<MarketAssetResponse> allMovers = new ArrayList<>(cached.values().stream()
                .flatMap(List::stream)
                .toList());

        Set<String> seenCodes = new HashSet<>(allMovers.stream().map(MarketAssetResponse::code).toList());
        for (MarketType type : MarketType.values()) {
            if (!cached.containsKey(type)) {
                MarketAssetProvider provider = providers.get(type);
                if (provider != null) {
                    for (MarketAssetResponse a : provider.getTopMovers(limit, true)) {
                        if (seenCodes.add(a.code())) allMovers.add(a);
                    }
                    for (MarketAssetResponse a : provider.getTopMovers(limit, false)) {
                        if (seenCodes.add(a.code())) allMovers.add(a);
                    }
                }
            }
        }

        List<MarketAssetResponse> indices = topMoversRedisService.getIndices();
        if (indices.isEmpty()) {
            MarketAssetProvider stockProvider = providers.get(MarketType.STOCK);
            if (stockProvider != null) {
                indices = stockProvider.getAll().stream()
                        .filter(a -> a.metadata() != null && "MAIN_INDEX".equals(a.metadata().get("stockSegment")))
                        .toList();
            }
        }
        indices = indices.stream().limit(limit).toList();

        List<MarketOverviewResponse.AssetTypeMovers> movers = new ArrayList<>();
        for (MarketType type : MarketType.values()) {
            Set<String> typeSeen = new HashSet<>();
            List<MarketAssetResponse> typeAssets = allMovers.stream()
                    .filter(a -> a.type() == type && a.changePercent() != null)
                    .filter(a -> type != MarketType.STOCK || !"MAIN_INDEX".equals(
                            a.metadata() != null ? a.metadata().get("stockSegment") : null))
                    .filter(a -> typeSeen.add(a.code()))
                    .toList();

            List<MarketAssetResponse> gainers = typeAssets.stream()
                    .filter(a -> a.changePercent().signum() > 0)
                    .sorted(Comparator.comparing(MarketAssetResponse::changePercent).reversed())
                    .limit(limit)
                    .toList();

            List<MarketAssetResponse> losers = typeAssets.stream()
                    .filter(a -> a.changePercent().signum() < 0)
                    .sorted(Comparator.comparing(MarketAssetResponse::changePercent))
                    .limit(limit)
                    .toList();

            if (!gainers.isEmpty() || !losers.isEmpty()) {
                movers.add(new MarketOverviewResponse.AssetTypeMovers(type.name(), gainers, losers));
            }
        }

        return new MarketOverviewResponse(indices, movers);
    }

    public List<Map<String, Object>> getGroupCounts(MarketType type) {
        MarketAssetProvider provider = providers.get(type);
        if (provider == null) return List.of();
        return provider.getGroupCounts();
    }

    public List<?> getHistory(MarketType type, String code, CandlePeriod period) {
        return switch (type) {
            case STOCK -> stockQueryService.getStockHistory(code, period);
            case CRYPTO -> cryptoQueryService.getCryptoHistory(code, period);
            case FUND -> fundQueryService.getFundHistory(code, period);
            case FOREX -> forexQueryService.getForexHistory(code, period);
        };
    }

    @Override
    public void onMarketDataUpdated(MarketType type) {
        MarketAssetProvider provider = providers.get(type);
        if (provider == null) return;

        try {
            List<MarketAssetResponse> topGainers = provider.getTopMovers(10, true);
            List<MarketAssetResponse> topLosers = provider.getTopMovers(10, false);
            List<MarketAssetResponse> combined = Stream.concat(topGainers.stream(), topLosers.stream()).toList();
            topMoversRedisService.updateMovers(type, combined);

            if (type == MarketType.STOCK) {
                List<MarketAssetResponse> all = provider.getAll();
                List<MarketAssetResponse> indexAssets = all.stream()
                        .filter(a -> a.metadata() != null && "MAIN_INDEX".equals(a.metadata().get("stockSegment")))
                        .toList();
                topMoversRedisService.updateIndices(indexAssets);
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
        return PagedResponse.of(List.of(), 0, 1, 0);
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
        Comparator<MarketAssetResponse> comparator = switch (resolveSort(sort)) {
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

    private String resolveSort(String sort) {
        if (sort == null || sort.isBlank()) return "changePercent";
        return sort;
    }
}
