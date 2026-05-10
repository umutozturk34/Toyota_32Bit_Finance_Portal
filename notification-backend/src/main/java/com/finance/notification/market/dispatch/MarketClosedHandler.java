package com.finance.notification.market.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketClosedPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketClosedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-closed";
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_CLOSED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof MarketClosedPayload p)) {
            throw new IllegalArgumentException(
                    "MarketClosedHandler expects MarketClosedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        String title = translator.translate("notif.marketClosed.title", label);
        String body = translator.translate("notif.marketClosed.body", label);
        String emailSubject = translator.translate("notif.email.subject", title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(), "displayLabel", label));
    }
}
