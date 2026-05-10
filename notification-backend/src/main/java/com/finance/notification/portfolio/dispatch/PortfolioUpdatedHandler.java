package com.finance.notification.portfolio.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PortfolioUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "portfolio-updated";

    private final Translator translator;

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
        String title = translator.translate("notif.portfolioUpdated.title");
        String body = formatBody(p.totalValue(), p.dailyPnl(), p.dailyPnlPercent());
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("title", title);
        templateData.put("body", body);
        if (p.totalValue() != null) templateData.put("totalValue", p.totalValue());
        if (p.dailyPnl() != null) templateData.put("dailyPnl", p.dailyPnl());
        if (p.dailyPnlPercent() != null) templateData.put("dailyPnlPercent", p.dailyPnlPercent());
        String emailSubject = translator.translate("notif.email.subject", title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                templateData);
    }

    private String formatBody(BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent) {
        if (totalValue == null) {
            return translator.translate("notif.portfolioUpdated.bodySnapshotOnly");
        }
        BigDecimal scaledTotal = totalValue.setScale(2, RoundingMode.HALF_UP);
        if (dailyPnl != null && dailyPnlPercent != null) {
            String sign = dailyPnl.signum() >= 0 ? "+" : "";
            return translator.translate("notif.portfolioUpdated.bodyFull",
                    scaledTotal,
                    sign + dailyPnl.setScale(2, RoundingMode.HALF_UP),
                    sign + dailyPnlPercent.setScale(2, RoundingMode.HALF_UP));
        }
        return translator.translate("notif.portfolioUpdated.bodyValueOnly", scaledTotal);
    }
}
