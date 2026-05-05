package com.finance.notification.watchlist.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class WatchlistDeltaHandler implements NotificationHandler {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

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

        String assetCode = p.assetCode() != null ? p.assetCode() : "?";
        String displayName = (p.assetName() != null && !p.assetName().isBlank())
                ? p.assetName() : assetCode.toUpperCase();
        MarketType marketType = p.marketType();
        String marketLabel = marketType != null ? marketType.displayLabel() : "Varlık";

        String priceFormatted = formatPrice(p.currentPrice());
        String lastSeenFormatted = formatPrice(p.lastSeenPrice());
        String deltaFormatted = formatPercent(p.deltaPercent());

        String title = String.format("%s takip listesi hareketi", displayName);
        String body = String.format("%s — %s anlık %s, önceki %s, değişim %s",
                marketLabel, displayName, priceFormatted, lastSeenFormatted, deltaFormatted);

        Map<String, Object> model = new HashMap<>();
        model.put("assetCode", assetCode);
        model.put("assetCodeUpper", assetCode.toUpperCase());
        model.put("assetName", displayName);
        model.put("image", p.image());
        model.put("marketLabel", marketLabel);
        model.put("priceFormatted", priceFormatted);
        model.put("lastSeenFormatted", lastSeenFormatted);
        model.put("deltaFormatted", deltaFormatted);
        model.put("isUp", p.deltaPercent() != null && p.deltaPercent().signum() >= 0);

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + displayName + " takip listesi hareketi",
                "watchlist-delta",
                model);
    }

    private static String formatPrice(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat money = NumberFormat.getNumberInstance(TR);
        money.setMaximumFractionDigits(value.compareTo(BigDecimal.ONE) < 0 ? 6 : 2);
        money.setMinimumFractionDigits(2);
        return "₺" + money.format(value);
    }

    private static String formatPercent(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(2);
        fmt.setMinimumFractionDigits(2);
        String prefix = value.signum() >= 0 ? "+" : "";
        return prefix + fmt.format(value) + "%";
    }
}
