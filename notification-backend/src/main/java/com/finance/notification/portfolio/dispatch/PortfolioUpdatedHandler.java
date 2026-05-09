package com.finance.notification.portfolio.dispatch;

import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.model.NotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
public class PortfolioUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "portfolio-updated";

    @Override
    public NotificationType type() {
        return NotificationType.PORTFOLIO_UPDATED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request) {
        if (!(request.payload() instanceof PortfolioUpdatedPayload p)) {
            throw new IllegalArgumentException(
                    "PortfolioUpdatedHandler expects PortfolioUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String title = "Portföyünüz güncellendi";
        String body = formatBody(p.totalValue(), p.dailyPnl(), p.dailyPnlPercent());
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("title", title);
        templateData.put("body", body);
        if (p.totalValue() != null) templateData.put("totalValue", p.totalValue());
        if (p.dailyPnl() != null) templateData.put("dailyPnl", p.dailyPnl());
        if (p.dailyPnlPercent() != null) templateData.put("dailyPnlPercent", p.dailyPnlPercent());
        return new RenderedNotification(
                title,
                body,
                "Finance Portal — " + title,
                EMAIL_TEMPLATE,
                templateData);
    }

    private static String formatBody(BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent) {
        if (totalValue == null) {
            return "Günlük portföy snapshot'unuz alındı.";
        }
        StringBuilder sb = new StringBuilder("Toplam değer ₺")
                .append(totalValue.setScale(2, RoundingMode.HALF_UP));
        if (dailyPnl != null && dailyPnlPercent != null) {
            String sign = dailyPnl.signum() >= 0 ? "+" : "";
            sb.append(" · Günlük ")
                    .append(sign)
                    .append(dailyPnl.setScale(2, RoundingMode.HALF_UP))
                    .append(" (")
                    .append(sign)
                    .append(dailyPnlPercent.setScale(2, RoundingMode.HALF_UP))
                    .append("%)");
        }
        sb.append('.');
        return sb.toString();
    }
}
