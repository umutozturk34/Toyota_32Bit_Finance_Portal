package com.finance.notification.reports.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts portfolio figures from Turkish lira to the report's target currency, mirroring the
 * frontend {@code useRateHistory} logic exactly so the PDF matches the on-screen charts.
 *
 * <p>For each value the most recent rate point with {@code date <= target} is used (forward-fill:
 * weekends/holidays reuse the last prior trading day). When the target date precedes all history the
 * earliest point is used — never today's spot. A TRY target is a pass-through; an unusable rate
 * (null/≤0) falls back to the original lira value so a missing rate never crashes the report.
 */
public final class ReportFxConverter {

    private static final int SCALE = 8;
    private static final String TRY = "TRY";

    private final String target;
    private final Map<String, List<ForexRatePoint>> series;

    /**
     * @param targetCurrency report currency ("TRY"/"USD"/"EUR"); TRY makes this a pass-through
     * @param series         rate history keyed by currency code (e.g. "USD"/"EUR"), each ASC by date
     */
    public ReportFxConverter(String targetCurrency, Map<String, List<ForexRatePoint>> series) {
        this.target = targetCurrency == null ? TRY : targetCurrency.toUpperCase(Locale.ROOT);
        this.series = series == null ? Map.of() : series;
    }

    /** The report's target currency code (upper-case; {@code "TRY"} when this converter is a pass-through). */
    public String target() {
        return target;
    }

    /**
     * Lira-per-unit selling rate of {@code currency} on {@code date}, or {@code null} when no history
     * exists. TRY is always 1. Forward-fills to the latest prior trading day; for dates before all
     * history the earliest point is returned.
     */
    public BigDecimal rateAt(String currency, LocalDate date) {
        if (currency == null || TRY.equalsIgnoreCase(currency)) return BigDecimal.ONE;
        List<ForexRatePoint> points = series.get(currency.toUpperCase(Locale.ROOT));
        BigDecimal historical = rateOn(points, date);
        if (historical != null) return historical;
        // Date precedes loaded history → use the earliest historical rate, never today's spot.
        if (points != null && !points.isEmpty()) return points.get(0).rate();
        return null;
    }

    /**
     * Converts a lira value to the target currency at {@code date}. TRY target (or a null value) is a
     * pass-through; an unusable rate (null/≤0) also returns the original lira value unchanged.
     */
    public BigDecimal convertFromTry(BigDecimal valueTry, LocalDate date) {
        if (valueTry == null || TRY.equals(target)) return valueTry;
        BigDecimal rate = rateAt(target, date);
        if (rate == null || rate.signum() <= 0) return valueTry;
        return valueTry.divide(rate, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Binary search for the most recent point with {@code date <= target}; returns its rate, or
     * {@code null} when the series is empty or every point is later than the target.
     */
    private static BigDecimal rateOn(List<ForexRatePoint> points, LocalDate target) {
        if (points == null || points.isEmpty() || target == null) return null;
        int lo = 0;
        int hi = points.size() - 1;
        BigDecimal answer = null;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (!points.get(mid).date().isAfter(target)) {
                answer = points.get(mid).rate();
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return answer;
    }
}
