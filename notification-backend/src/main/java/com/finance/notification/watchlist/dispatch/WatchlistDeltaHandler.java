package com.finance.notification.watchlist.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WatchlistDeltaHandler implements NotificationHandler {

    private static final String FALLBACK_ASSET_CODE = "?";

    private final NotificationDispatchProperties properties;
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.WATCHLIST_DELTA;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof WatchlistDeltaPayload p)) {
            throw new IllegalArgumentException(
                    "WatchlistDeltaHandler expects WatchlistDeltaPayload, got "
                            + request.payload().getClass().getSimpleName());
        }

        String marketLabel = p.marketType() != null
                ? translator.translate("market.type." + p.marketType().name(), locale)
                : translator.translate("notif.fallback.assetLabel", locale);
        String listLabel = resolveListLabel(p, locale);
        int count = p.items().size();
        String title = buildTitle(locale, listLabel, p.items(), count);

        return new RenderedNotification(
                title,
                buildBody(locale, p.items()),
                translator.translate("notif.watchlistDelta.emailSubject", locale, listLabel),
                "watchlist-delta",
                Map.of(
                        "watchlistName", listLabel,
                        "marketLabel", marketLabel,
                        "itemCount", count,
                        "items", renderItems(p.items(), locale)));
    }

    private String resolveListLabel(WatchlistDeltaPayload p, Locale locale) {
        if (p.defaultList()) return translator.translate("watchlist.defaultName", locale);
        if (p.watchlistName() != null && !p.watchlistName().isBlank()) return p.watchlistName();
        return translator.translate("notif.watchlistDelta.fallbackListLabel", locale);
    }

    private String buildTitle(Locale locale, String listLabel, List<WatchlistDeltaPayload.DeltaItem> items, int count) {
        if (count == 1) {
            return translator.translate("notif.watchlistDelta.titleSingle", locale, listLabel, displayName(items.get(0)));
        }
        return translator.translate("notif.watchlistDelta.titleMultiple", locale, listLabel, count);
    }

    private String buildBody(Locale locale, List<WatchlistDeltaPayload.DeltaItem> items) {
        List<String> parts = new ArrayList<>();
        int previewLimit = Math.min(items.size(), properties.watchlistDelta().bodyPreviewItems());
        for (int i = 0; i < previewLimit; i++) {
            WatchlistDeltaPayload.DeltaItem item = items.get(i);
            parts.add(String.format("%s %s", displayName(item), formatPercent(item.deltaPercent(), locale)));
        }
        if (items.size() > properties.watchlistDelta().bodyPreviewItems()) {
            parts.add(translator.translate("notif.watchlistDelta.andMore", locale, items.size() - properties.watchlistDelta().bodyPreviewItems()));
        }
        return String.join(" · ", parts);
    }

    private List<Map<String, Object>> renderItems(List<WatchlistDeltaPayload.DeltaItem> items, Locale locale) {
        List<Map<String, Object>> rendered = new ArrayList<>(items.size());
        for (WatchlistDeltaPayload.DeltaItem item : items) {
            rendered.add(renderItem(item, locale));
        }
        return rendered;
    }

    private Map<String, Object> renderItem(WatchlistDeltaPayload.DeltaItem item, Locale locale) {
        BigDecimal delta = item.deltaPercent();
        Map<String, Object> row = new HashMap<>();
        row.put("assetCode", item.assetCode());
        row.put("assetCodeUpper", item.assetCode() != null ? item.assetCode().toUpperCase() : "");
        row.put("assetName", displayName(item));
        row.put("image", item.image());
        row.put("priceFormatted", formatPrice(item.currentPrice(), locale));
        row.put("lastSeenFormatted", formatPrice(item.lastSeenPrice(), locale));
        row.put("deltaFormatted", formatPercent(delta, locale));
        row.put("isUp", delta != null && delta.signum() >= 0);
        return row;
    }

    private String displayName(WatchlistDeltaPayload.DeltaItem item) {
        if (item.assetName() != null && !item.assetName().isBlank()) return item.assetName();
        return item.assetCode() != null ? item.assetCode().toUpperCase() : FALLBACK_ASSET_CODE;
    }

    private String formatPrice(BigDecimal value, Locale locale) {
        if (value == null) return "—";
        NumberFormat fmt = NumberFormat.getNumberInstance(locale);
        fmt.setMaximumFractionDigits(value.abs().compareTo(BigDecimal.ONE) < 0 ? properties.formatting().fractionDigitsSmall() : properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        return "₺" + fmt.format(value);
    }

    private String formatPercent(BigDecimal value, Locale locale) {
        if (value == null) return "—";
        BigDecimal scaled = value.setScale(properties.formatting().fractionDigitsLarge(), RoundingMode.HALF_UP);
        NumberFormat fmt = NumberFormat.getNumberInstance(locale);
        fmt.setMaximumFractionDigits(properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        String prefix = scaled.signum() > 0 ? "+" : "";
        return prefix + fmt.format(scaled) + "%";
    }
}
