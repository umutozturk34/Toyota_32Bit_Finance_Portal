package com.finance.notification.market.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/** Renders {@link NotificationType#MARKET_OPENED} notifications with a localized market label. */
@Component
@RequiredArgsConstructor
public class MarketOpenedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-opened";
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_OPENED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof MarketOpenedPayload p)) {
            throw new IllegalArgumentException(
                    "MarketOpenedHandler expects MarketOpenedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.market() != null
                ? translator.translate("market.type." + p.market(), locale, p.market())
                : "";
        String title = translator.translate("notif.marketOpened.title", locale, label);
        String body = translator.translate("notif.marketOpened.body", locale, label);
        String emailSubject = translator.translate("notif.email.subject", locale, title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(), "displayLabel", label));
    }
}
