package com.finance.notification.reports.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Locale-aware currency formatter used in PDF templates. Large values are abbreviated (K/M/B) and
 * grouping/decimal separators follow the locale (Turkish uses {@code .}/{@code ,}); a real minus
 * sign is used for negatives.
 */
public final class MoneyFormat {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final BigDecimal BILLION = new BigDecimal("1000000000");

    private final String symbol;
    private final char groupSep;
    private final char decimalSep;

    public MoneyFormat(String symbol, Locale locale) {
        this.symbol = symbol;
        boolean tr = locale != null && "tr".equalsIgnoreCase(locale.getLanguage());
        this.groupSep = tr ? '.' : ',';
        this.decimalSep = tr ? ',' : '.';
    }

    public String of(BigDecimal value) {
        if (value == null) return "—";
        BigDecimal abs = value.abs();
        String sign = value.signum() < 0 ? "−" : "";
        String body;
        if (abs.compareTo(BILLION) >= 0) {
            body = compact(abs, BILLION, "B");
        } else if (abs.compareTo(MILLION) >= 0) {
            body = compact(abs, MILLION, "M");
        } else if (abs.compareTo(THOUSAND) >= 0) {
            body = compact(abs, THOUSAND, "K");
        } else {
            body = fullDecimal(abs, 2);
        }
        return sign + symbol + " " + body;
    }

    /** Like {@link #of} but prefixes a {@code +} for non-negative values, for signed P/L display. */
    public String signed(BigDecimal value) {
        if (value == null) return "—";
        String prefix = value.signum() >= 0 ? "+" : "";
        return prefix + of(value);
    }

    private String compact(BigDecimal absValue, BigDecimal divisor, String suffix) {
        BigDecimal scaled = absValue.divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() < 0) scaled = scaled.setScale(0, RoundingMode.HALF_UP);
        return formatPlain(scaled) + suffix;
    }

    private String fullDecimal(BigDecimal absValue, int decimals) {
        return formatPlain(absValue.setScale(decimals, RoundingMode.HALF_UP));
    }

    private String formatPlain(BigDecimal value) {
        String plain = value.toPlainString();
        boolean neg = plain.startsWith("-");
        if (neg) plain = plain.substring(1);
        String[] parts = plain.split("\\.");
        String intPart = parts[0];
        StringBuilder grouped = new StringBuilder();
        int len = intPart.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) grouped.append(groupSep);
            grouped.append(intPart.charAt(i));
        }
        if (parts.length > 1 && !parts[1].isEmpty()) {
            grouped.append(decimalSep).append(parts[1]);
        }
        return (neg ? "-" : "") + grouped.toString();
    }
}
