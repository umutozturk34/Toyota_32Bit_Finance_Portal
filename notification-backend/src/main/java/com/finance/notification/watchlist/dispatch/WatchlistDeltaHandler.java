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

        MarketType marketType = p.marketType();
        String marketLabel = marketType != null ? marketType.displayLabel() : "Varlık";
        String listLabel = (p.watchlistName() != null && !p.watchlistName().isBlank())
                ? p.watchlistName() : "Takip listeniz";

        int count = p.items().size();
        String title = count == 1
                ? String.format("%s — %s", listLabel, displayName(p.items().get(0)))
                : String.format("%s — %d varlıkta hareket", listLabel, count);

        String body = buildBody(p.items());
        List<Map<String, Object>> renderedItems = renderItems(p.items());

        Map<String, Object> model = new HashMap<>();
        model.put("watchlistName", listLabel);
        model.put("marketLabel", marketLabel);
        model.put("itemCount", count);
        model.put("items", renderedItems);

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + listLabel + " hareketi",
                "watchlist-delta",
                model);
    }

    private static String buildBody(List<WatchlistDeltaPayload.DeltaItem> items) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < items.size() && i < BODY_PREVIEW_ITEMS; i++) {
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
            BigDecimal delta = item.deltaPercent();
            boolean isUp = delta != null && delta.signum() >= 0;
            Map<String, Object> row = new HashMap<>();
            row.put("assetCode", item.assetCode());
            row.put("assetCodeUpper", item.assetCode() != null ? item.assetCode().toUpperCase() : "");
            row.put("assetName", displayName(item));
            row.put("image", item.image());
            row.put("priceFormatted", formatPrice(item.currentPrice()));
            row.put("lastSeenFormatted", formatPrice(item.lastSeenPrice()));
            row.put("deltaFormatted", formatPercent(delta));
            row.put("isUp", isUp);
            rendered.add(row);
        }
        return rendered;
    }

    private static String displayName(WatchlistDeltaPayload.DeltaItem item) {
        if (item.assetName() != null && !item.assetName().isBlank()) return item.assetName();
        return item.assetCode() != null ? item.assetCode().toUpperCase() : "?";
    }

    private static String formatPrice(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat money = NumberFormat.getNumberInstance(TR);
        money.setMaximumFractionDigits(value.abs().compareTo(BigDecimal.ONE) < 0 ? 6 : 2);
        money.setMinimumFractionDigits(2);
        return "₺" + money.format(value);
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) return "—";
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(2);
        fmt.setMinimumFractionDigits(2);
        String prefix = scaled.signum() > 0 ? "+" : "";
        return prefix + fmt.format(scaled) + "%";
    }
}
