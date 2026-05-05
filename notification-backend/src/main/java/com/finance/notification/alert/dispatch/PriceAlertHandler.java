package com.finance.notification.alert.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
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
        if (!(request.payload() instanceof PriceAlertPayload p)) {
            throw new IllegalArgumentException(
                    "PriceAlertHandler expects PriceAlertPayload, got " + request.payload().getClass().getSimpleName());
        }

        String assetCode = p.assetCode() != null ? p.assetCode() : "?";
        String assetName = p.assetName();
        AlertDirection direction = p.direction() != null ? p.direction() : AlertDirection.ABOVE;
        MarketType marketType = p.marketType();

        String marketLabel = marketType != null ? marketType.displayLabel() : "Varlık";
        String directionLabel = direction.displayLabel();
        String thresholdFormatted = formatPrice(p.threshold(), direction.isPercentBased());
        String priceFormatted = formatPrice(p.currentPrice(), false);
        String changePercent = computeChangePercent(p.currentPrice(), p.threshold(), direction);

        String displayName = (assetName != null && !assetName.isBlank()) ? assetName : assetCode.toUpperCase();
        String title = String.format("%s alarmı tetiklendi", displayName);
        String body = String.format("%s — %s. Eşik %s, anlık %s.",
                marketLabel, directionLabel, thresholdFormatted, priceFormatted);

        Map<String, Object> model = new HashMap<>();
        model.put("assetCode", assetCode);
        model.put("assetCodeUpper", assetCode.toUpperCase());
        model.put("assetName", displayName);
        model.put("image", p.image());
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
