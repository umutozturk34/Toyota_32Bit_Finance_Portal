package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class PriceAlertEvaluator {

    private final PriceAlertService priceAlertService;
    private final NotificationDispatcher dispatcher;
    private final AssetSnapshotCache assetSnapshotCache;

    @Transactional
    public int evaluate(MarketType marketType) {
        List<PriceAlert> alerts = priceAlertService.activeAlerts(marketType);
        if (alerts.isEmpty()) {
            return 0;
        }
        Set<String> codes = alerts.stream().map(PriceAlert::getAssetCode).collect(Collectors.toUnmodifiableSet());
        Map<String, AssetSnapshot> snapshots = assetSnapshotCache.findByCodes(marketType, codes);
        log.info("PriceAlert evaluation type={} requestedCodes={} snapshotsFound={}",
                marketType, codes, snapshots.keySet());
        int fired = 0;
        for (PriceAlert alert : alerts) {
            AssetSnapshot snapshot = snapshots.get(alert.getAssetCode());
            if (snapshot == null) {
                log.info("Skip alert id={} code={} reason=no_snapshot", alert.getId(), alert.getAssetCode());
                continue;
            }
            if (snapshot.priceTry() == null) {
                log.info("Skip alert id={} code={} reason=null_priceTry snapshot={}",
                        alert.getId(), alert.getAssetCode(), snapshot);
                continue;
            }
            log.info("Evaluate alert id={} code={} dir={} threshold={} currentPrice={}",
                    alert.getId(), alert.getAssetCode(), alert.getDirection(),
                    alert.getThreshold(), snapshot.priceTry());
            if (alert.evaluate(snapshot.priceTry())) {
                alert.markFired();
                priceAlertService.persist(alert);
                PriceAlertPayload payload = new PriceAlertPayload(
                        alert.getId(),
                        marketType,
                        alert.getAssetCode(),
                        alert.getDirection(),
                        alert.getThreshold(),
                        snapshot.priceTry(),
                        snapshot.image(),
                        snapshot.name());
                dispatcher.dispatch(NotificationRequest.of(alert.getUserSub(), payload));
                fired++;
            }
        }
        log.debug("Price alert evaluation marketType={} alerts={} fired={}",
                marketType, alerts.size(), fired);
        return fired;
    }
}
