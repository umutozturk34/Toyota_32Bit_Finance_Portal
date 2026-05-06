package com.finance.notification.market.dispatch;

import com.finance.common.exception.BusinessException;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketClosedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-closed";

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_CLOSED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof MarketClosedPayload p)) {
            throw new BusinessException(
                    "MarketClosedHandler expects MarketClosedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        String title = label + " piyasası kapandı";
        String body = label + " seansı sona erdi, kapanış fiyatları yüklendi.";
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(), "displayLabel", label));
    }
}
