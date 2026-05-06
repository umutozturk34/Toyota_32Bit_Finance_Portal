package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketOpenedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-opened";

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_OPENED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof MarketOpenedPayload p)) {
            throw new IllegalArgumentException(
                    "MarketOpenedHandler expects MarketOpenedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        String title = label + " piyasası açıldı";
        String body = label + " piyasası açıldı, açılış fiyatları yüklendi.";
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(), "displayLabel", label));
    }
}
