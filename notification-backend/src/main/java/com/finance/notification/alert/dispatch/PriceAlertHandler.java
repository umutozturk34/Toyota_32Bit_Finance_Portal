package com.finance.notification.alert.dispatch;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PriceAlertHandler implements NotificationHandler {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String FALLBACK_ASSET_CODE = "?";
    private static final String FALLBACK_MARKET_LABEL = "Varlık";

    private final NotificationDispatchProperties properties;

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

        AlertDirection direction = p.direction() != null ? p.direction() : AlertDirection.ABOVE;
        MarketType marketType = p.marketType();
        String assetCode = p.assetCode() != null ? p.assetCode() : FALLBACK_ASSET_CODE;
        String displayName = displayName(p.assetName(), assetCode);
        String marketLabel = marketType != null ? marketType.displayLabel() : FALLBACK_MARKET_LABEL;

        return new RenderedNotification(
                String.format("%s alarmı tetiklendi", displayName),
                buildBody(marketLabel, direction, p.threshold(), p.currentPrice()),
                "Finance Portal — " + displayName + " " + direction.displayLabel().toLowerCase(),
                "price-alert",
                buildModel(p, direction, marketLabel, assetCode, displayName));
    }

    private String buildBody(String marketLabel, AlertDirection direction,
                                    BigDecimal threshold, BigDecimal currentPrice) {
        return String.format("%s — %s. Eşik %s, anlık %s.",
                marketLabel,
                direction.displayLabel(),
                formatPrice(threshold, direction.isPercentBased()),
                formatPrice(currentPrice, false));
    }

    private Map<String, Object> buildModel(PriceAlertPayload p, AlertDirection direction,
                                                   String marketLabel, String assetCode, String displayName) {
        Map<String, Object> model = new HashMap<>();
        model.put("assetCode", assetCode);
        model.put("assetCodeUpper", assetCode.toUpperCase());
        model.put("assetName", displayName);
        model.put("image", p.image());
        model.put("marketLabel", marketLabel);
        model.put("direction", direction.name());
        model.put("directionLabel", direction.displayLabel());
        model.put("thresholdFormatted", formatPrice(p.threshold(), direction.isPercentBased()));
        model.put("priceFormatted", formatPrice(p.currentPrice(), false));
        model.put("changePercent", computeChangePercent(p.currentPrice(), p.threshold(), direction));
        model.put("isUp", direction.isUpward());
        model.put("isPercent", direction.isPercentBased());
        return model;
    }

    private static String displayName(String assetName, String assetCode) {
        if (assetName != null && !assetName.isBlank()) return assetName;
        return assetCode.toUpperCase();
    }

    private String formatPrice(BigDecimal value, boolean asPercent) {
        if (value == null) return "—";
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        if (asPercent) {
            fmt.setMaximumFractionDigits(properties.formatting().fractionDigitsLarge());
            fmt.setMinimumFractionDigits(0);
            return "%" + fmt.format(value);
        }
        fmt.setMaximumFractionDigits(value.compareTo(BigDecimal.ONE) < 0 ? properties.formatting().fractionDigitsSmall() : properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        return "₺" + fmt.format(value);
    }

    private String computeChangePercent(BigDecimal current, BigDecimal threshold, AlertDirection direction) {
        if (direction.isPercentBased() || current == null || threshold == null || threshold.signum() == 0) return null;
        BigDecimal pct = current.subtract(threshold)
                .divide(threshold, properties.formatting().changePercentScale(), RoundingMode.HALF_UP)
                .multiply(HUNDRED);
        NumberFormat fmt = NumberFormat.getNumberInstance(TR);
        fmt.setMaximumFractionDigits(properties.formatting().fractionDigitsLarge());
        fmt.setMinimumFractionDigits(properties.formatting().fractionDigitsLarge());
        String prefix = pct.signum() >= 0 ? "+" : "";
        return prefix + fmt.format(pct) + "%";
    }
}
