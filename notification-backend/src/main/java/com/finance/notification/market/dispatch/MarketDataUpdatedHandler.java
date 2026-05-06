package com.finance.notification.market.dispatch;

import com.finance.common.exception.BusinessException;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketDataUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-data-updated";

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_DATA_UPDATED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof MarketDataUpdatedPayload p)) {
            throw new BusinessException(
                    "MarketDataUpdatedHandler expects MarketDataUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        String title = label + " verileri güncellendi";
        String body = label + " piyasası için fiyat verileri yenilendi.";
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(), "displayLabel", label));
    }
}
