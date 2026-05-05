package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.watchlist.model.WatchlistItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class WatchlistEvaluator {

    private final WatchlistService watchlistService;
    private final NotificationDispatcher dispatcher;
    private final AssetSnapshotCache assetSnapshotCache;

    @Value("${notification.watchlist.default-delta-percent:5.0}")
    private BigDecimal globalDeltaThreshold;

    @Transactional
    public int evaluate(MarketType marketType) {
        List<WatchlistItem> items = watchlistService.itemsForMarket(marketType);
        if (items.isEmpty()) {
            return 0;
        }
        Set<String> codes = items.stream().map(WatchlistItem::getAssetCode).collect(Collectors.toUnmodifiableSet());
        Map<String, AssetSnapshot> snapshots = assetSnapshotCache.findByCodes(marketType, codes);
        int notified = 0;
        for (WatchlistItem item : items) {
            AssetSnapshot snapshot = snapshots.get(item.getAssetCode());
            if (snapshot == null || snapshot.priceTry() == null) continue;
            BigDecimal currentPrice = snapshot.priceTry();
            if (item.exceedsThreshold(currentPrice, globalDeltaThreshold)) {
                BigDecimal deltaPct = item.deltaPercent(currentPrice).orElse(BigDecimal.ZERO);
                WatchlistDeltaPayload payload = new WatchlistDeltaPayload(
                        item.getId(),
                        marketType,
                        item.getAssetCode(),
                        item.getLastSeenPrice(),
                        currentPrice,
                        deltaPct,
                        snapshot.image(),
                        snapshot.name());
                dispatcher.dispatch(NotificationRequest.of(item.getUserSub(), payload));
                notified++;
            }
            item.recordObservation(currentPrice);
            watchlistService.persist(item);
        }
        log.debug("Watchlist evaluation marketType={} items={} notified={}",
                marketType, items.size(), notified);
        return notified;
    }
}
