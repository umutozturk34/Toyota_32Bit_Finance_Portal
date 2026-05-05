package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class WatchlistEvaluator {

    private final WatchlistService watchlistService;
    private final WatchlistRepository watchlistRepository;
    private final NotificationDispatcher dispatcher;
    private final AssetSnapshotCache assetSnapshotCache;
    private final WatchlistItemMapper watchlistItemMapper;

    @Value("${notification.watchlist.default-delta-percent:5.0}")
    private BigDecimal globalDeltaThreshold;

    private record GroupKey(String userSub, Long watchlistId) {}

    @Transactional
    public int evaluate(MarketType marketType) {
        List<WatchlistItem> items = watchlistService.itemsForMarket(marketType);
        if (items.isEmpty()) {
            return 0;
        }
        Map<String, AssetSnapshot> snapshots = loadSnapshots(marketType, items);
        Map<GroupKey, List<WatchlistDeltaPayload.DeltaItem>> firedByGroup = collectFires(items, snapshots);
        int notifications = dispatchAll(firedByGroup, marketType);
        log.debug("Watchlist evaluation marketType={} items={} notifications={}",
                marketType, items.size(), notifications);
        return notifications;
    }

    private Map<String, AssetSnapshot> loadSnapshots(MarketType marketType, List<WatchlistItem> items) {
        Set<String> codes = items.stream()
                .map(WatchlistItem::getAssetCode)
                .collect(Collectors.toUnmodifiableSet());
        return assetSnapshotCache.findByCodes(marketType, codes);
    }

    private Map<GroupKey, List<WatchlistDeltaPayload.DeltaItem>> collectFires(
            List<WatchlistItem> items, Map<String, AssetSnapshot> snapshots) {
        Map<GroupKey, List<WatchlistDeltaPayload.DeltaItem>> firedByGroup = new LinkedHashMap<>();
        for (WatchlistItem item : items) {
            AssetSnapshot snapshot = snapshots.get(item.getAssetCode());
            if (snapshot == null || snapshot.priceTry() == null) continue;
            BigDecimal currentPrice = snapshot.priceTry();
            if (item.exceedsThreshold(currentPrice, globalDeltaThreshold)) {
                BigDecimal deltaPct = item.deltaPercent(currentPrice).orElse(BigDecimal.ZERO);
                GroupKey key = new GroupKey(item.getUserSub(), item.getWatchlistId());
                firedByGroup.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(watchlistItemMapper.toFiredItem(item, snapshot, currentPrice, deltaPct));
            }
            item.recordObservation(currentPrice);
            watchlistService.persist(item);
        }
        return firedByGroup;
    }

    private int dispatchAll(Map<GroupKey, List<WatchlistDeltaPayload.DeltaItem>> firedByGroup, MarketType marketType) {
        if (firedByGroup.isEmpty()) return 0;
        Map<Long, String> watchlistNames = resolveWatchlistNames(firedByGroup.keySet());
        int notifications = 0;
        for (Map.Entry<GroupKey, List<WatchlistDeltaPayload.DeltaItem>> entry : firedByGroup.entrySet()) {
            GroupKey key = entry.getKey();
            WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                    key.watchlistId(),
                    watchlistNames.get(key.watchlistId()),
                    marketType,
                    entry.getValue());
            dispatcher.dispatch(NotificationRequest.of(key.userSub(), payload));
            notifications++;
        }
        return notifications;
    }

    private Map<Long, String> resolveWatchlistNames(Set<GroupKey> groups) {
        Set<Long> ids = groups.stream().map(GroupKey::watchlistId).collect(Collectors.toUnmodifiableSet());
        if (ids.isEmpty()) return Map.of();
        return watchlistRepository.findAllById(ids).stream()
                .collect(Collectors.toUnmodifiableMap(Watchlist::getId, Watchlist::getName, (a, b) -> a));
    }
}
