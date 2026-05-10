package com.finance.notification.market.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.dispatch.slot.SlotResolver;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarketDataUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-data-updated";

    private final SlotResolver slotResolver;
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_DATA_UPDATED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof MarketDataUpdatedPayload p)) {
            throw new IllegalArgumentException(
                    "MarketDataUpdatedHandler expects MarketDataUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.market() != null
                ? translator.translate("market.type." + p.market(), locale, p.market())
                : "";
        Optional<String> slot = slotResolver.slotFor(p.source());
        String title = slot
                .map(s -> translator.translate("notif.marketDataUpdated.titleWithSlot", locale, label, translator.translate("notif.slot." + s, locale)))
                .orElseGet(() -> translator.translate("notif.marketDataUpdated.title", locale, label));
        String body = slot
                .map(s -> translator.translate("notif.marketDataUpdated.bodyWithSlot", locale, label, translator.translate("notif.slot." + s, locale)))
                .orElseGet(() -> translator.translate("notif.marketDataUpdated.body", locale, label));
        String emailSubject = translator.translate("notif.email.subject", locale, title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(),
                        "displayLabel", label, "slot", slot.orElse("")));
    }
}
