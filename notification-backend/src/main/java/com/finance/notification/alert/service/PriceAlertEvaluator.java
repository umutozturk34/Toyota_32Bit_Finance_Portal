package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
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
    private final PriceAlertMapper priceAlertMapper;

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
            if (fireIfTriggered(alert, snapshots.get(alert.getAssetCode()), marketType)) {
                fired++;
            }
        }
        log.debug("Price alert evaluation marketType={} alerts={} fired={}",
                marketType, alerts.size(), fired);
        return fired;
    }

    private boolean fireIfTriggered(PriceAlert alert, AssetSnapshot snapshot, MarketType marketType) {
        if (snapshot == null) {
            log.info("Skip alert id={} code={} reason=no_snapshot", alert.getId(), alert.getAssetCode());
            return false;
        }
        if (snapshot.priceTry() == null) {
            log.info("Skip alert id={} code={} reason=null_priceTry snapshot={}",
                    alert.getId(), alert.getAssetCode(), snapshot);
            return false;
        }
        log.info("Evaluate alert id={} code={} dir={} threshold={} currentPrice={}",
                alert.getId(), alert.getAssetCode(), alert.getDirection(),
                alert.getThreshold(), snapshot.priceTry());
        if (!alert.evaluate(snapshot.priceTry())) {
            return false;
        }
        alert.markFired();
        priceAlertService.persist(alert);
        PriceAlertPayload payload = priceAlertMapper.toFiredPayload(alert, snapshot, marketType);
        dispatcher.dispatch(NotificationRequest.of(alert.getUserSub(), payload));
        return true;
    }
}
