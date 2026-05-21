package com.finance.notification.macro.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationHandler;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MacroIndicatorsUpdatedPayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MacroIndicatorsUpdatedHandler implements NotificationHandler {

    private static final String EMAIL_TEMPLATE = "macro-indicators-updated";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM");

    private final Translator translator;

    @Override
    public NotificationType type() {
        return NotificationType.MACRO_INDICATORS_UPDATED;
    }

    @Override
    public RenderedNotification render(NotificationRequest request, Locale locale) {
        if (!(request.payload() instanceof MacroIndicatorsUpdatedPayload p)) {
            throw new IllegalArgumentException(
                    "MacroIndicatorsUpdatedHandler expects MacroIndicatorsUpdatedPayload, got "
                            + request.payload().getClass().getSimpleName());
        }
        List<IndicatorChange> sorted = sortBySignificance(p.changes());
        int count = sorted.size();
        long upCount = sorted.stream().filter(c -> c.direction() == IndicatorChange.Direction.UP).count();
        long downCount = sorted.stream().filter(c -> c.direction() == IndicatorChange.Direction.DOWN).count();

        String title = count > 0
                ? translator.translate("notif.macroIndicators.titleWithCount", locale, count)
                : translator.translate("notif.macroIndicators.title", locale);
        String body = count > 0
                ? translator.translate("notif.macroIndicators.bodyWithCount", locale, count)
                : translator.translate("notif.macroIndicators.body", locale);
        String emailSubject = translator.translate("notif.email.subject", locale, title);
        List<Map<String, Object>> rows = renderRows(sorted, locale);

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("title", title);
        templateData.put("body", body);
        templateData.put("rows", rows);
        templateData.put("changedCount", count);
        templateData.put("upCount", (int) upCount);
        templateData.put("downCount", (int) downCount);
        templateData.put("hasHero", count > 0);
        if (count > 0) {
            templateData.put("hero", rows.get(0));
        }
        templateData.put("hintLabel", translator.translate("notif.macroIndicators.hint", locale));
        templateData.put("columnLabel", translator.translate("notif.macroIndicators.column.indicator", locale));
        templateData.put("columnPrev", translator.translate("notif.macroIndicators.column.previous", locale));
        templateData.put("columnCurr", translator.translate("notif.macroIndicators.column.current", locale));
        templateData.put("columnDelta", translator.translate("notif.macroIndicators.column.change", locale));
        templateData.put("summaryUp", translator.translate("notif.macroIndicators.summary.up", locale, (int) upCount));
        templateData.put("summaryDown", translator.translate("notif.macroIndicators.summary.down", locale, (int) downCount));
        templateData.put("summaryTotal", translator.translate("notif.macroIndicators.summary.total", locale, count));
        templateData.put("heroLabel", translator.translate("notif.macroIndicators.heroLabel", locale));
        return new RenderedNotification(title, body, emailSubject, EMAIL_TEMPLATE, templateData);
    }

    private static List<IndicatorChange> sortBySignificance(List<IndicatorChange> changes) {
        if (changes == null) return List.of();
        List<IndicatorChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparing((IndicatorChange c) ->
                c.deltaPercent() == null ? BigDecimal.ZERO : c.deltaPercent().abs()).reversed());
        return sorted;
    }

    private List<Map<String, Object>> renderRows(List<IndicatorChange> changes, Locale locale) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (IndicatorChange change : changes) {
            if (change == null) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("code", change.code());
            row.put("label", translatedLabel(change, locale));
            row.put("category", change.category());
            row.put("unit", change.unit());
            row.put("currency", change.currency());
            row.put("maturity", change.maturity());
            row.put("categoryLabel", categoryLabel(change, locale));
            row.put("accent", accentFor(change));
            row.put("newValue", formatValue(locale, change.newValue(), change.unit()));
            row.put("previousValue", change.previousValue() == null
                    ? "—"
                    : formatValue(locale, change.previousValue(), change.unit()));
            row.put("newDate", change.newDate() == null ? "" : change.newDate().format(DATE_FORMAT.withLocale(locale)));
            row.put("previousDate", change.previousDate() == null ? "" : change.previousDate().format(DATE_FORMAT.withLocale(locale)));
            row.put("delta", formatDelta(locale, change.deltaAbsolute(), change.unit()));
            row.put("deltaPercent", formatPercent(locale, change.deltaPercent()));
            row.put("direction", change.direction().name());
            row.put("isUp", change.direction() == IndicatorChange.Direction.UP);
            row.put("isDown", change.direction() == IndicatorChange.Direction.DOWN);
            row.put("arrow", arrowFor(change.direction()));
            result.add(row);
        }
        return result;
    }

    private String translatedLabel(IndicatorChange change, Locale locale) {
        String key = "marketOverview.macro." + change.label();
        String translated = translator.translate(key, locale);
        if (translated == null || translated.equals(key)) return change.label();
        return translated;
    }

    private String categoryLabel(IndicatorChange change, Locale locale) {
        if (change.category() == null) return "";
        String key = "notif.macroIndicators.category." + change.category();
        String translated = translator.translate(key, locale);
        if (translated == null || translated.equals(key)) return change.category();
        return translated;
    }

    private static String accentFor(IndicatorChange change) {
        if (change.category() == null) return "#6366f1";
        return switch (change.category()) {
            case "INFLATION" -> "#f59e0b";
            case "RATES" -> "#6366f1";
            case "DEPOSIT" -> switch (change.currency() == null ? "TRY" : change.currency()) {
                case "USD" -> "#06b6d4";
                case "EUR" -> "#8b5cf6";
                default -> "#10b981";
            };
            default -> "#6366f1";
        };
    }

    private static String arrowFor(IndicatorChange.Direction direction) {
        return switch (direction) {
            case UP -> "▲";
            case DOWN -> "▼";
            case FLAT -> "•";
        };
    }

    private static String formatValue(Locale locale, BigDecimal value, String unit) {
        if (value == null) return "—";
        int scale = "PERCENT".equals(unit) ? 2 : 4;
        String suffix = "PERCENT".equals(unit) ? "%" : "";
        return formatNumber(locale, value, scale) + suffix;
    }

    private static String formatDelta(Locale locale, BigDecimal delta, String unit) {
        if (delta == null) return "—";
        int scale = "PERCENT".equals(unit) ? 2 : 4;
        String sign = delta.signum() > 0 ? "+" : "";
        String suffix = "PERCENT".equals(unit) ? " pp" : "";
        return sign + formatNumber(locale, delta, scale) + suffix;
    }

    private static String formatPercent(Locale locale, BigDecimal percent) {
        if (percent == null) return "—";
        String sign = percent.signum() > 0 ? "+" : "";
        return sign + formatNumber(locale, percent, 2) + "%";
    }

    private static String formatNumber(Locale locale, BigDecimal value, int scale) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        NumberFormat formatter = NumberFormat.getNumberInstance(locale);
        formatter.setMinimumFractionDigits(scale);
        formatter.setMaximumFractionDigits(scale);
        return formatter.format(scaled);
    }
}
