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
import org.springframework.beans.factory.annotation.Value;
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

    // Keyset scan batch size: how many active alerts are pulled into memory (with their snapshots) at a time.
    // Bounds peak memory on the per-tick scan regardless of how many active alerts a market accumulates.
    @Value("${notification.alert.scan-page-size:500}")
    private int scanPageSize;

    /**
     * Scans active alerts for the market in bounded keyset pages, fires those whose threshold is crossed, and
     * dispatches each page's fires. Keyset (id-ascending) paging — not offset — is used because firing an alert
     * deactivates it mid-scan, which would shift an offset window and skip rows.
     *
     * @return the number of notifications successfully dispatched across all pages
     */
    @Transactional
    public int evaluate(MarketType marketType) {
        long lastId = 0L;
        int totalDispatched = 0;
        while (true) {
            List<PriceAlert> alerts = priceAlertService.activeAlertsAfter(marketType, lastId, scanPageSize);
            if (alerts.isEmpty()) break;
            // Alerts come back id-ascending, so the last one is the high-water mark for the next page's cursor.
            lastId = alerts.get(alerts.size() - 1).getId();

            Set<String> codes = alerts.stream()
                    .map(PriceAlert::getAssetCode)
                    .collect(Collectors.toUnmodifiableSet());
            Map<String, AssetSnapshot> snapshots = assetSnapshotCache.findByCodes(marketType, codes);

            List<NotificationRequest> firedRequests = collectFires(alerts, snapshots, marketType);
            if (!firedRequests.isEmpty()) {
                BatchResult result = dispatcher.dispatchBatched(firedRequests);
                totalDispatched += result.dispatched();
                log.info("Price alert dispatch type={} fires={} dispatched={} failed={}",
                        marketType, firedRequests.size(), result.dispatched(), result.failed());
            }
            if (alerts.size() < scanPageSize) break;
        }
        return totalDispatched;
    }

    private List<NotificationRequest> collectFires(List<PriceAlert> alerts,
                                                   Map<String, AssetSnapshot> snapshots,
                                                   MarketType marketType) {
        List<NotificationRequest> fired = new ArrayList<>();
        for (PriceAlert alert : alerts) {
            AssetSnapshot snapshot = resolveSnapshot(alert, snapshots);
            if (snapshot == null) continue;
            if (!alert.evaluate(snapshot.priceTry())) continue;
            // Isolate each alert: one bad row must not abort the whole batch.
            try {
                alert.markFired();
                priceAlertService.persist(alert);
                PriceAlertPayload payload = priceAlertMapper.toFiredPayload(alert, snapshot, marketType);
                fired.add(NotificationRequest.of(alert.getUserSub(), payload));
                log.info("Price alert fired alertId={} userSub={} market={} code={} dir={} threshold={} price={}",
                        alert.getId(), alert.getUserSub(), marketType, alert.getAssetCode(),
                        alert.getDirection(), alert.getThreshold(), snapshot.priceTry());
            } catch (RuntimeException ex) {
                log.warn("Price alert fire failed alertId={} code={}: {}",
                        alert.getId(), alert.getAssetCode(), ex.getMessage());
            }
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
