package com.finance.notification.watchlist.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.common.model.MarketType;
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

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final String FALLBACK_ASSET_CODE = "?";

    private final NotificationDispatchProperties properties;
    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.WATCHLIST_DELTA;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof WatchlistDeltaPayload p)) {
            throw new IllegalArgumentException(
                    "WatchlistDeltaHandler expects WatchlistDeltaPayload, got "
                            + request.payload().getClass().getSimpleName());
        }

        String marketLabel = p.marketType() != null ? p.marketType().displayLabel() : translator.translate("notif.fallback.assetLabel");
        String listLabel = (p.watchlistName() != null && !p.watchlistName().isBlank())
                ? p.watchlistName() : translator.translate("notif.watchlistDelta.fallbackListLabel");
        int count = p.items().size();
        String title = buildTitle(listLabel, p.items(), count);

        return new RenderedNotification(
                title,
                buildBody(p.items()),
                translator.translate("notif.watchlistDelta.emailSubject", listLabel),
                "watchlist-delta",
                Map.of(
                        "watchlistName", listLabel,
                        "marketLabel", marketLabel,
                        "itemCount", count,
                        "items", renderItems(p.items())));
    }

    private String buildTitle(String listLabel, List<WatchlistDeltaPayload.DeltaItem> items, int count) {
        if (count == 1) {
            return translator.translate("notif.watchlistDelta.titleSingle", listLabel, displayName(items.get(0)));
        }
        return translator.translate("notif.watchlistDelta.titleMultiple", listLabel, count);
    }

    private String buildBody(List<WatchlistDeltaPayload.DeltaItem> items) {
        List<String> parts = new ArrayList<>();
        int previewLimit = Math.min(items.size(), properties.watchlistDelta().bodyPreviewItems());
        for (int i = 0; i < previewLimit; i++) {
            WatchlistDeltaPayload.DeltaItem item = items.get(i);
            parts.add(String.format("%s %s", displayName(item), formatPercent(item.deltaPercent())));
        }
        if (items.size() > properties.watchlistDelta().bodyPreviewItems()) {
            parts.add(translator.translate("notif.watchlistDelta.andMore", items.size() - properties.watchlistDelta().bodyPreviewItems()));
        }
        return String.join(" · ", parts);
    }

    private List<Map<String, Object>> renderItems(List<WatchlistDeltaPayload.DeltaItem> items) {
        List<Map<String, Object>> rendered = new ArrayList<>(items.size());
        for (WatchlistDeltaPayload.DeltaItem item : items) {
            rendered.add(renderItem(item));
        }
        return rendered;
    }

    private Map<String, Object> renderItem(WatchlistDeltaPayload.DeltaItem item) {
        BigDecimal delta = item.deltaPercent();
        Map<String, Object> row = new HashMap<>();
        row.put("assetCode", item.assetCode());
        row.put("assetCodeUpper", item.assetCode() != null ? item.assetCode().toUpperCase() : "");
        row.put("assetName", displayName(item));
        row.put("image", item.image());
        row.put("priceFormatted", formatPrice(item.currentPrice()));
        row.put("lastSeenFormatted", formatPrice(item.lastSeenPrice()));
        row.put("deltaFormatted", formatPercent(delta));
        row.put("isUp", delta != null && delta.signum() >= 0);
        return row;
    }

    private String displayName(WatchlistDeltaPayload.DeltaItem item) {
        if (item.assetName() != null && !item.assetName().isBlank()) return item.assetName();
        return item.assetCode() != null ? item.assetCode().toUpperCase() : FALLBACK_ASSET_CODE;
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(value.abs().compareTo(BigDecimal.ONE) < 0 ? properties.formatting().fractionDigitsSmall() : properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        return "₺" + fmt.format(value);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "—";
        BigDecimal scaled = value.setScale(properties.formatting().fractionDigitsLarge(), RoundingMode.HALF_UP);
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        String prefix = scaled.signum() > 0 ? "+" : "";
        return prefix + fmt.format(scaled) + "%";
    }
}
