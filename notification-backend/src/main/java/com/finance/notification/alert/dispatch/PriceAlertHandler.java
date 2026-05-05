package com.finance.notification.alert.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class PriceAlertHandler implements NotificationHandler {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    @Override
    public NotificationType type() {
        return NotificationType.PRICE_ALERT_FIRED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        Map<String, Object> data = request.data();
        String assetCode = String.valueOf(data.getOrDefault("assetCode", "?"));
        String assetName = (String) data.getOrDefault("assetName", null);
        String image = (String) data.getOrDefault("image", null);
        AlertDirection direction = parseDirection(data.get("direction"));
        MarketType marketType = parseMarketType(data.get("marketType"));
        BigDecimal threshold = toDecimal(data.get("threshold"));
        BigDecimal currentPrice = toDecimal(data.get("currentPrice"));

        String marketLabel = marketType != null ? marketType.displayLabel() : "Varlık";
        String directionLabel = direction.displayLabel();
        String thresholdFormatted = formatPrice(threshold, direction.isPercentBased());
        String priceFormatted = formatPrice(currentPrice, false);
        String changePercent = computeChangePercent(currentPrice, threshold, direction);

        String displayName = (assetName != null && !assetName.isBlank()) ? assetName : assetCode.toUpperCase();
        String title = String.format("%s alarmı tetiklendi", displayName);
        String body = String.format("%s — %s. Eşik %s, anlık %s.",
                marketLabel, directionLabel, thresholdFormatted, priceFormatted);

        Map<String, Object> model = new HashMap<>();
        model.put("assetCode", assetCode);
        model.put("assetCodeUpper", assetCode.toUpperCase());
        model.put("assetName", displayName);
        model.put("image", image);
        model.put("marketLabel", marketLabel);
        model.put("direction", direction.name());
        model.put("directionLabel", directionLabel);
        model.put("thresholdFormatted", thresholdFormatted);
        model.put("priceFormatted", priceFormatted);
        model.put("changePercent", changePercent);
        model.put("isUp", direction.isUpward());
        model.put("isPercent", direction.isPercentBased());

        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + displayName + " " + directionLabel.toLowerCase(),
                "price-alert",
                model);
    }

    private static AlertDirection parseDirection(Object value) {
        if (value == null) return AlertDirection.ABOVE;
        try {
            return AlertDirection.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return AlertDirection.ABOVE;
        }
    }

    private static MarketType parseMarketType(Object value) {
        if (value == null) return null;
        try {
            return MarketType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatPrice(BigDecimal value, boolean asPercent) {
        if (value == null) return "—";
        if (asPercent) {
            NumberFormat pct = NumberFormat.getNumberInstance(TR);
            pct.setMaximumFractionDigits(2);
            pct.setMinimumFractionDigits(0);
            return "%" + pct.format(value);
        }
        NumberFormat money = NumberFormat.getNumberInstance(TR);
        money.setMaximumFractionDigits(value.compareTo(BigDecimal.ONE) < 0 ? 6 : 2);
        money.setMinimumFractionDigits(2);
        return "₺" + money.format(value);
    }

    private static String computeChangePercent(BigDecimal current, BigDecimal threshold, AlertDirection direction) {
        if (direction.isPercentBased() || current == null || threshold == null || threshold.signum() == 0) return null;
        BigDecimal pct = current.subtract(threshold)
                .divide(threshold, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(2);
        fmt.setMinimumFractionDigits(2);
        String prefix = pct.signum() >= 0 ? "+" : "";
        return prefix + fmt.format(pct) + "%";
    }
}
