package com.finance.notification.alert.service;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
public class PriceAlertEvaluator {

    private final PriceAlertService priceAlertService;
    private final NotificationDispatcher dispatcher;

    @Transactional
    public int evaluate(MarketType marketType, Map<String, BigDecimal> latestPrices) {
        if (latestPrices.isEmpty()) {
            return 0;
        }
        List<PriceAlert> alerts = priceAlertService.activeAlerts(marketType);
        int fired = 0;
        for (PriceAlert alert : alerts) {
            BigDecimal currentPrice = latestPrices.get(alert.getAssetCode());
            if (currentPrice == null) continue;
            if (alert.evaluate(currentPrice)) {
                alert.markFired();
                priceAlertService.persist(alert);
                dispatcher.dispatch(NotificationRequest.of(
                        alert.getUserSub(),
                        NotificationType.PRICE_ALERT_FIRED,
                        Map.of(
                                "alertId", alert.getId(),
                                "marketType", marketType.name(),
                                "assetCode", alert.getAssetCode(),
                                "direction", alert.getDirection().name(),
                                "threshold", alert.getThreshold(),
                                "currentPrice", currentPrice
                        )
                ));
                fired++;
            }
        }
        log.debug("Price alert evaluation marketType={} prices={} fired={}",
                marketType, latestPrices.size(), fired);
        return fired;
    }
}
