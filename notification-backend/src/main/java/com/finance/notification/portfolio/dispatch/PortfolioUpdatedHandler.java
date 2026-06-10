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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders {@link NotificationType#PORTFOLIO_UPDATED} notifications, choosing a morning/evening/default
 * title from the event source and formatting total value and daily P/L into the localized body.
 */
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
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof PortfolioUpdatedPayload p)) {
            throw new IllegalArgumentException(
                    "PortfolioUpdatedHandler expects PortfolioUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        String title = translator.translate(titleKey(p.source()), locale);
        String body = formatBody(locale, p.totalValue(), p.dailyPnl(), p.dailyPnlPercent());
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("title", title);
        templateData.put("body", body);
        if (p.totalValue() != null) templateData.put("totalValue", p.totalValue());
        if (p.dailyPnl() != null) templateData.put("dailyPnl", p.dailyPnl());
        if (p.dailyPnlPercent() != null) templateData.put("dailyPnlPercent", p.dailyPnlPercent());
        templateData.put("portfolioCount", p.portfolioCount());
        templateData.put("portfolios", emailRows(p.portfolios()));
        String emailSubject = translator.translate("notif.email.subject", locale, title);
        return new RenderedNotification(
                title,
                body,
                emailSubject,
                EMAIL_TEMPLATE,
                templateData);
    }

    private static String titleKey(String source) {
        return switch (source) {
            case "morning" -> "notif.portfolioUpdated.titleMorning";
            case "evening" -> "notif.portfolioUpdated.titleEvening";
            case null, default -> "notif.portfolioUpdated.title";
        };
    }

    private String formatBody(Locale locale, BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlPercent) {
        if (totalValue == null) {
            return translator.translate("notif.portfolioUpdated.bodySnapshotOnly", locale);
        }
        String totalText = formatNumber(locale, totalValue, 2);
        if (dailyPnl != null && dailyPnlPercent != null) {
            String sign = dailyPnl.signum() >= 0 ? "+" : "";
            return translator.translate("notif.portfolioUpdated.bodyFull",
                    locale,
                    totalText,
                    sign + formatNumber(locale, dailyPnl, 2),
                    sign + formatNumber(locale, dailyPnlPercent, 2));
        }
        return translator.translate("notif.portfolioUpdated.bodyValueOnly", locale, totalText);
    }

    private static String formatNumber(Locale locale, BigDecimal value, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        NumberFormat formatter = NumberFormat.getNumberInstance(locale);
        formatter.setMinimumFractionDigits(scale);
        formatter.setMaximumFractionDigits(scale);
        return formatter.format(scaled);
    }

    /**
     * Flattens the per-portfolio lines into template maps (name + raw value/P/L) so the email can render each
     * portfolio as its own themed row; the template formats the numbers and picks up/down colors itself.
     */
    private static List<Map<String, Object>> emailRows(List<PortfolioUpdatedPayload.Line> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>(lines.size());
        for (PortfolioUpdatedPayload.Line line : lines) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", line.name());
            row.put("totalValue", line.totalValue());
            row.put("dailyPnl", line.dailyPnl());
            row.put("dailyPnlPercent", line.dailyPnlPercent());
            rows.add(row);
        }
        return rows;
    }
}
