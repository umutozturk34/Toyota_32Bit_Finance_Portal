package com.finance.notification.market.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class MarketDataUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "market-data-updated";

    /**
     * Ordered keyword → slot table. Order matters because the matcher returns
     * the first hit; an unordered {@link Map} would risk classifying
     * "afternoon-noon-fallback" sources non-deterministically.
     */
    private static final List<Map.Entry<String, String>> SLOT_KEYWORDS = List.of(
            Map.entry("morning", "sabah"),
            Map.entry("sabah", "sabah"),
            Map.entry("afternoon", "öğlen"),
            Map.entry("midday", "öğlen"),
            Map.entry("noon", "öğlen"),
            Map.entry("öğle", "öğlen"),
            Map.entry("ogle", "öğlen"),
            Map.entry("evening", "akşam"),
            Map.entry("aksam", "akşam"),
            Map.entry("akşam", "akşam"),
            Map.entry("daily", "günlük"),
            Map.entry("full", "günlük"));

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
        for (Map.Entry<String, String> entry : SLOT_KEYWORDS) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        log.warn("MarketDataUpdatedHandler unknown source token={} — falling back to generic title", source);
        return null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
