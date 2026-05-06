package com.finance.notification.market.dispatch;

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
            throw new IllegalArgumentException(
                    "MarketDataUpdatedHandler expects MarketDataUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String label = p.displayLabel() != null ? p.displayLabel() : p.market();
        String slot = slotFor(p.source());
        String title = (slot != null)
                ? label + " · " + capitalize(slot) + " güncellemesi"
                : label + " verileri güncellendi";
        String body = (slot != null)
                ? label + " piyasası " + slot + " güncellemesi yayımlandı, son fiyatlar yenilendi."
                : label + " piyasası için fiyat verileri yenilendi.";
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body, "market", p.market(),
                        "displayLabel", label, "slot", slot != null ? slot : ""));
    }

    static String slotFor(String source) {
        if (source == null) return null;
        String lower = source.toLowerCase();
        if (lower.contains("morning") || lower.contains("sabah")) return "sabah";
        if (lower.contains("afternoon") || lower.contains("midday") || lower.contains("noon")
                || lower.contains("öğle") || lower.contains("ogle")) return "öğlen";
        if (lower.contains("evening") || lower.contains("aksam") || lower.contains("akşam")) return "akşam";
        if (lower.contains("daily") || lower.contains("full")) return "günlük";
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
