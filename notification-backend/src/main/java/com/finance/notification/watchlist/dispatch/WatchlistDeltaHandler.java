package com.finance.notification.watchlist.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.core.model.NotificationType;
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
public class WatchlistDeltaHandler implements NotificationHandler {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final int BODY_PREVIEW_ITEMS = 3;
    private static final int FRACTION_DIGITS_LARGE = 2;
    private static final int FRACTION_DIGITS_SMALL = 6;
    private static final String FALLBACK_LIST_LABEL = "Takip listeniz";
    private static final String FALLBACK_MARKET_LABEL = "Varlık";
    private static final String FALLBACK_ASSET_CODE = "?";

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

        String marketLabel = p.marketType() != null ? p.marketType().displayLabel() : FALLBACK_MARKET_LABEL;
        String listLabel = (p.watchlistName() != null && !p.watchlistName().isBlank())
                ? p.watchlistName() : FALLBACK_LIST_LABEL;
        int count = p.items().size();

        return new RenderedNotification(
                buildTitle(listLabel, p.items(), count),
                buildBody(p.items()),
                "Finance Portal — " + listLabel + " hareketi",
                "watchlist-delta",
                Map.of(
                        "watchlistName", listLabel,
                        "marketLabel", marketLabel,
                        "itemCount", count,
                        "items", renderItems(p.items())));
    }

    private static String buildTitle(String listLabel, List<WatchlistDeltaPayload.DeltaItem> items, int count) {
        if (count == 1) {
            return String.format("%s — %s", listLabel, displayName(items.get(0)));
        }
        return String.format("%s — %d varlıkta hareket", listLabel, count);
    }

    private static String buildBody(List<WatchlistDeltaPayload.DeltaItem> items) {
        List<String> parts = new ArrayList<>();
        int previewLimit = Math.min(items.size(), BODY_PREVIEW_ITEMS);
        for (int i = 0; i < previewLimit; i++) {
            WatchlistDeltaPayload.DeltaItem item = items.get(i);
            parts.add(String.format("%s %s", displayName(item), formatPercent(item.deltaPercent())));
        }
        if (items.size() > BODY_PREVIEW_ITEMS) {
            parts.add(String.format("ve %d daha", items.size() - BODY_PREVIEW_ITEMS));
        }
        return String.join(" · ", parts);
    }

    private static List<Map<String, Object>> renderItems(List<WatchlistDeltaPayload.DeltaItem> items) {
        List<Map<String, Object>> rendered = new ArrayList<>(items.size());
        for (WatchlistDeltaPayload.DeltaItem item : items) {
            rendered.add(renderItem(item));
        }
        return rendered;
    }

    private static Map<String, Object> renderItem(WatchlistDeltaPayload.DeltaItem item) {
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

    private static String displayName(WatchlistDeltaPayload.DeltaItem item) {
        if (item.assetName() != null && !item.assetName().isBlank()) return item.assetName();
        return item.assetCode() != null ? item.assetCode().toUpperCase() : FALLBACK_ASSET_CODE;
    }

    private static String formatPrice(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(value.abs().compareTo(BigDecimal.ONE) < 0 ? FRACTION_DIGITS_SMALL : FRACTION_DIGITS_LARGE);
        fmt.setMinimumFractionDigits(FRACTION_DIGITS_LARGE);
        return "₺" + fmt.format(value);
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) return "—";
        BigDecimal scaled = value.setScale(FRACTION_DIGITS_LARGE, RoundingMode.HALF_UP);
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(FRACTION_DIGITS_LARGE);
        fmt.setMinimumFractionDigits(FRACTION_DIGITS_LARGE);
        String prefix = scaled.signum() > 0 ? "+" : "";
        return prefix + fmt.format(scaled) + "%";
    }
}
