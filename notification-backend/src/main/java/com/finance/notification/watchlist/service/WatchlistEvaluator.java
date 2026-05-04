package com.finance.notification.watchlist.service;

import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.watchlist.model.WatchlistItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class WatchlistEvaluator {

    private final WatchlistService watchlistService;
    private final NotificationDispatcher dispatcher;

    @Value("${notification.watchlist.default-delta-percent:5.0}")
    private BigDecimal globalDeltaThreshold;

    @Transactional
    public int evaluate(MarketType marketType, Map<String, BigDecimal> latestPrices) {
        if (latestPrices.isEmpty()) {
            return 0;
        }
        List<WatchlistItem> items = watchlistService.itemsForMarket(marketType);
        int notified = 0;
        for (WatchlistItem item : items) {
            BigDecimal currentPrice = latestPrices.get(item.getAssetCode());
            if (currentPrice == null) continue;
            if (item.exceedsThreshold(currentPrice, globalDeltaThreshold)) {
                BigDecimal deltaPct = item.deltaPercent(currentPrice).orElse(BigDecimal.ZERO);
                dispatcher.dispatch(NotificationRequest.of(
                        item.getUserSub(),
                        NotificationType.WATCHLIST_DELTA,
                        Map.of(
                                "watchlistId", item.getId(),
                                "marketType", marketType.name(),
                                "assetCode", item.getAssetCode(),
                                "lastSeenPrice", item.getLastSeenPrice(),
                                "currentPrice", currentPrice,
                                "deltaPercent", deltaPct
                        )
                ));
                notified++;
            }
            item.recordObservation(currentPrice);
            watchlistService.persist(item);
        }
        log.debug("Watchlist evaluation marketType={} prices={} notified={}",
                marketType, latestPrices.size(), notified);
        return notified;
    }
}
