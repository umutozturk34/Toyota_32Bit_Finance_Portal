package com.finance.notification.news.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.NewsPublishedPayload;
import com.finance.notification.core.dispatch.slot.SlotResolver;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NewsPublishedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "news-published";

    private final SlotResolver slotResolver;

    @Override
    public NotificationType type() {
        return NotificationType.NEWS_PUBLISHED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof NewsPublishedPayload p)) {
            throw new IllegalArgumentException(
                    "NewsPublishedHandler expects NewsPublishedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        int count = Math.max(p.articleCount(), 0);
        Optional<String> slot = slotResolver.slotFor(p.source());
        String title = slot
                .map(s -> slotResolver.capitalize(s) + " haberleri"
                        + (count > 0 ? " · " + count + " yeni başlık" : ""))
                .orElseGet(() -> count > 0
                        ? count + " yeni haber yayımlandı"
                        : "Yeni haberler yayımlandı");
        String body = count > 0
                ? "Finans gündeminden " + count + " yeni başlık akışınıza eklendi."
                : "Finans gündeminden yeni başlıklar akışınıza eklendi.";
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body,
                        "articleCount", count,
                        "slot", slot.orElse("")));
    }
}
