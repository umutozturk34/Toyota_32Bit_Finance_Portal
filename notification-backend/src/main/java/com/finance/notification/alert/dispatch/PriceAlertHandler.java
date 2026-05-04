package com.finance.notification.alert.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PriceAlertHandler implements NotificationHandler {

    @Override
    public NotificationType type() {
        return NotificationType.PRICE_ALERT_FIRED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        Map<String, Object> data = request.data();
        String assetCode = String.valueOf(data.getOrDefault("assetCode", "?"));
        String direction = String.valueOf(data.getOrDefault("direction", "?"));
        Object threshold = data.getOrDefault("threshold", "?");
        Object currentPrice = data.getOrDefault("currentPrice", "?");

        String title = "Fiyat alarmı: " + assetCode;
        String body = String.format("%s alarmı tetiklendi. Hedef %s, anlık %s.",
                directionLabel(direction), threshold, currentPrice);

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — fiyat alarmı (" + assetCode + ")",
                "price-alert",
                Map.of(
                        "assetCode", assetCode,
                        "direction", direction,
                        "threshold", threshold,
                        "currentPrice", currentPrice
                ));
    }

    private String directionLabel(String direction) {
        return switch (direction) {
            case "ABOVE" -> "Üstüne çıkma";
            case "BELOW" -> "Altına düşme";
            case "CHANGE_PCT_UP" -> "Yüzde yükseliş";
            case "CHANGE_PCT_DOWN" -> "Yüzde düşüş";
            default -> direction;
        };
    }
}
