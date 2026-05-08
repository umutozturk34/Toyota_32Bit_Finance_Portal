package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.app.dto.response.overview.WatchlistData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.app.watchlist.model.OverviewWatchlist;
import com.finance.app.watchlist.model.OverviewWatchlistItem;
import com.finance.app.watchlist.repository.OverviewWatchlistItemRepository;
import com.finance.app.watchlist.repository.OverviewWatchlistRepository;
import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class WatchlistWidgetProvider implements OverviewWidgetProvider {

    private static final Sort DISPLAY_SORT = Sort.by(Sort.Direction.ASC, "displayOrder");
    private static final String FALLBACK_NAME = "Liste bulunamadı";

    private final OverviewWatchlistRepository watchlistRepository;
    private final OverviewWatchlistItemRepository itemRepository;
    private final AssetSnapshotCache assetSnapshotCache;
    private final OverviewDefaults defaults;

    @Override
    public WidgetKind kind() {
        return WidgetKind.WATCHLIST;
    }

    @Override
    @Transactional(readOnly = true)
    public WatchlistData fetch(String userSub, WidgetSection section) {
        Optional<OverviewWatchlist> resolved = resolveTargetList(userSub, section);
        if (resolved.isEmpty()) {
            return new WatchlistData(null, FALLBACK_NAME, List.of());
        }
        OverviewWatchlist list = resolved.get();
        int limit = readLimit(section);
        List<OverviewWatchlistItem> items = itemRepository.findByWatchlistId(list.getId(), DISPLAY_SORT).stream()
                .limit(limit).toList();
        if (items.isEmpty()) {
            return new WatchlistData(list.getId(), list.getName(), List.of());
        }
        return new WatchlistData(list.getId(), list.getName(), enrich(items));
    }

    private Optional<OverviewWatchlist> resolveTargetList(String userSub, WidgetSection section) {
        Long requestedId = readWatchlistId(section);
        if (requestedId != null) {
            return watchlistRepository.findByIdAndUserSub(requestedId, userSub);
        }
        return watchlistRepository.findByUserSubOrderByIsDefaultDescIdAsc(userSub).stream().findFirst();
    }

    private Long readWatchlistId(WidgetSection section) {
        JsonNode node = section.config().get("watchlistId");
        if (node == null || node.isNull() || !node.isNumber()) return null;
        long value = node.asLong();
        return value <= 0 ? null : value;
    }

    private int readLimit(WidgetSection section) {
        JsonNode node = section.config().get("limit");
        if (node == null || !node.isInt() || node.asInt() <= 0) return defaults.defaultWatchlistLimit();
        return Math.min(node.asInt(), OverviewDefaults.MAX_CONFIG_LIMIT);
    }

    private List<WatchlistData.WatchlistRow> enrich(List<OverviewWatchlistItem> items) {
        Map<MarketType, Set<String>> codesByMarket = items.stream()
                .collect(Collectors.groupingBy(
                        OverviewWatchlistItem::getMarketType,
                        Collectors.mapping(OverviewWatchlistItem::getAssetCode, Collectors.toUnmodifiableSet())));
        Map<MarketType, Map<String, AssetSnapshot>> snapshots = new java.util.EnumMap<>(MarketType.class);
        codesByMarket.forEach((market, codes) -> snapshots.put(market, assetSnapshotCache.findByCodes(market, codes)));

        List<WatchlistData.WatchlistRow> rows = new ArrayList<>(items.size());
        for (OverviewWatchlistItem item : items) {
            AssetSnapshot snapshot = snapshots.getOrDefault(item.getMarketType(), Map.of()).get(item.getAssetCode());
            BigDecimal price = snapshot != null ? snapshot.priceTry() : null;
            BigDecimal change = snapshot != null ? snapshot.changePercent() : null;
            String image = snapshot != null ? snapshot.image() : null;
            rows.add(new WatchlistData.WatchlistRow(item.getAssetCode(), item.getMarketType(), image, price, change));
        }
        return rows;
    }
}
