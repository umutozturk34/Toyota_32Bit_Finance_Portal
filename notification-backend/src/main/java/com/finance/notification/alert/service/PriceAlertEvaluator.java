package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationDispatcher.BatchResult;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates active price alerts for a market against the latest snapshot cache and dispatches a
 * notification for each one that crosses its threshold. A fired alert is marked triggered (and
 * deactivated) so it does not re-fire until reactivated. Snapshot prices are compared in TRY.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class PriceAlertEvaluator {

    private final PriceAlertService priceAlertService;
    private final NotificationDispatcher dispatcher;
    private final AssetSnapshotCache assetSnapshotCache;
    private final PriceAlertMapper priceAlertMapper;

    /**
     * Loads active alerts for the market, fires those whose threshold is crossed and dispatches
     * them in a single batch.
     *
     * @return the number of notifications successfully dispatched
     */
    @Transactional
    public int evaluate(MarketType marketType) {
        List<PriceAlert> alerts = priceAlertService.activeAlerts(marketType);
        if (alerts.isEmpty()) return 0;

        Set<String> codes = alerts.stream()
                .map(PriceAlert::getAssetCode)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, AssetSnapshot> snapshots = assetSnapshotCache.findByCodes(marketType, codes);

        List<NotificationRequest> firedRequests = collectFires(alerts, snapshots, marketType);
        if (firedRequests.isEmpty()) return 0;

        BatchResult result = dispatcher.dispatchBatched(firedRequests);
        log.info("Price alert dispatch type={} fires={} dispatched={} failed={}",
                marketType, firedRequests.size(), result.dispatched(), result.failed());
        return result.dispatched();
    }

    private List<NotificationRequest> collectFires(List<PriceAlert> alerts,
                                                   Map<String, AssetSnapshot> snapshots,
                                                   MarketType marketType) {
        List<NotificationRequest> fired = new ArrayList<>();
        for (PriceAlert alert : alerts) {
            AssetSnapshot snapshot = resolveSnapshot(alert, snapshots);
            if (snapshot == null) continue;
            if (!alert.evaluate(snapshot.priceTry())) continue;
            alert.markFired();
            priceAlertService.persist(alert);
            PriceAlertPayload payload = priceAlertMapper.toFiredPayload(alert, snapshot, marketType);
            fired.add(NotificationRequest.of(alert.getUserSub(), payload));
            log.info("Price alert fired alertId={} userSub={} market={} code={} dir={} threshold={} price={}",
                    alert.getId(), alert.getUserSub(), marketType, alert.getAssetCode(),
                    alert.getDirection(), alert.getThreshold(), snapshot.priceTry());
        }
        return fired;
    }

    private AssetSnapshot resolveSnapshot(PriceAlert alert, Map<String, AssetSnapshot> snapshots) {
        AssetSnapshot snapshot = snapshots.get(alert.getAssetCode());
        if (snapshot == null) {
            log.info("Skip alert id={} code={} reason=no_snapshot", alert.getId(), alert.getAssetCode());
            return null;
        }
        if (snapshot.priceTry() == null) {
            log.info("Skip alert id={} code={} reason=null_priceTry", alert.getId(), alert.getAssetCode());
            return null;
        }
        return snapshot;
    }
}
