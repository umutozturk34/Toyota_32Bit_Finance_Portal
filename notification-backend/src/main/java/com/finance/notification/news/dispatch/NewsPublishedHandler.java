package com.finance.notification.news.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.NewsPublishedPayload;
import com.finance.notification.core.dispatch.slot.SlotResolver;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NewsPublishedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "news-published";

    private final SlotResolver slotResolver;
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.NEWS_PUBLISHED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof NewsPublishedPayload p)) {
            throw new IllegalArgumentException(
                    "NewsPublishedHandler expects NewsPublishedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        int count = Math.max(p.articleCount(), 0);
        Optional<String> slot = slotResolver.slotFor(p.source());
        String title;
        if (slot.isPresent()) {
            String slotName = translator.translate("notif.slot." + slot.get(), locale);
            title = count > 0
                    ? translator.translate("notif.newsPublished.titleSlotWithCount", locale, slotName, count)
                    : translator.translate("notif.newsPublished.titleSlot", locale, slotName);
        } else {
            title = count > 0
                    ? translator.translate("notif.newsPublished.titleWithCount", locale, count)
                    : translator.translate("notif.newsPublished.title", locale);
        }
        String body = count > 0
                ? translator.translate("notif.newsPublished.bodyWithCount", locale, count)
                : translator.translate("notif.newsPublished.body", locale);
        String emailSubject = translator.translate("notif.email.subject", locale, title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                Map.of("title", title, "body", body,
                        "articleCount", count,
                        "slot", slot.orElse("")));
    }
}
