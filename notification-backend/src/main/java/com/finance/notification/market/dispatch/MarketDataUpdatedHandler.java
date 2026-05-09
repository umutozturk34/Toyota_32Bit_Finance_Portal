package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.dispatch.slot.SlotResolver;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarketDataUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-data-updated";

    private final SlotResolver slotResolver;

    @Override
    public NotificationType type() {
        return NotificationType.MARKET_DATA_UPDATED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof MarketDataUpdatedPayload p)) {
            throw new IllegalArgumentException(
                    "MarketDataUpdatedHandler expects MarketDataUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        Optional<String> slot = slotResolver.slotFor(p.source());
        String title = slot
                .map(s -> label + " · " + slotResolver.capitalize(s) + " güncellemesi")
                .orElseGet(() -> label + " verileri güncellendi");
        String body = slot
                .map(s -> label + " piyasası " + s + " güncellemesi yayımlandı, son fiyatlar yenilendi.")
                .orElseGet(() -> label + " piyasası için fiyat verileri yenilendi.");
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(),
                        "displayLabel", label, "slot", slot.orElse("")));
    }
}
