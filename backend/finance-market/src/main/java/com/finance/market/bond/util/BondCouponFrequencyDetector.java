package com.finance.market.bond.util;

import com.finance.market.bond.model.BondRateHistory;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Infers a floating bond's coupon cadence from the periodic ex-coupon DROPS in its daily clean-price history.
 * A TLREF/auction floater's price accretes the coupon as it nears a payment date, then drops sharply back toward
 * par on the ex-date — a sawtooth. Those coupon drops are far larger (observed ~7-21%) than ordinary market
 * moves (&lt;~3%), so a single threshold cleanly isolates them; the median spacing between consecutive drops, in
 * months, is the coupon period. This reads the ACTUAL cadence per bond rather than assuming a type-based default.
 *
 * <p>Returns {@code 0} (undetermined) when the history is too short or the drops are too irregular to read — the
 * caller then falls back to the {@code BondType} default — so a noisy/young series never forces a wrong cadence.
 */
public final class BondCouponFrequencyDetector {

    /** Single-day fractional price fall above which a move is a coupon drop, not market noise (coupon drops ≫ this). */
    private static final BigDecimal DROP_THRESHOLD = new BigDecimal("-0.04");
    /** Need at least this many coupon drops (≥2 gaps) before a median spacing is trustworthy. */
    private static final int MIN_DROPS = 3;
    /** Standard Türkiye Hazine coupon periods in months, matched against the observed median gap. */
    private static final int[] STANDARD_MONTHS = {1, 3, 6, 12};
    private static final double DAYS_PER_MONTH = 30.4;
    /** A median gap is accepted only if it is within this fraction of a standard period (else cadence is unclear). */
    private static final double MATCH_TOLERANCE = 0.30;

    private BondCouponFrequencyDetector() {
    }

    /**
     * The coupon period in months (1/3/6/12) inferred from the price-drop spacing, or {@code 0} when it cannot be
     * read confidently. {@code history} need not be pre-sorted.
     */
    public static int detectStepMonths(List<BondRateHistory> history) {
        if (history == null || history.size() < MIN_DROPS + 1) {
            return 0;
        }
        List<BondRateHistory> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparing(BondRateHistory::getRateDate));

        List<Long> gapDays = new ArrayList<>();
        BigDecimal prevPrice = null;
        java.time.LocalDate lastDropDate = null;
        for (BondRateHistory row : sorted) {
            BigDecimal price = row.getPrice();
            if (price == null || price.signum() <= 0) {
                continue;
            }
            if (prevPrice != null && prevPrice.signum() > 0) {
                BigDecimal change = price.subtract(prevPrice).divide(prevPrice, java.math.MathContext.DECIMAL64);
                if (change.compareTo(DROP_THRESHOLD) < 0) {
                    if (lastDropDate != null) {
                        gapDays.add(ChronoUnit.DAYS.between(lastDropDate, row.getRateDate()));
                    }
                    lastDropDate = row.getRateDate();
                }
            }
            prevPrice = price;
        }
        if (gapDays.size() < MIN_DROPS - 1) {
            return 0;
        }
        double medianDays = median(gapDays);
        return nearestStandardMonths(medianDays / DAYS_PER_MONTH);
    }

    /** The standard coupon period (months) closest to {@code months}, or 0 when none is within tolerance. */
    private static int nearestStandardMonths(double months) {
        int best = 0;
        double bestErr = Double.MAX_VALUE;
        for (int candidate : STANDARD_MONTHS) {
            double err = Math.abs(months - candidate) / candidate;
            if (err < bestErr) {
                bestErr = err;
                best = candidate;
            }
        }
        return bestErr <= MATCH_TOLERANCE ? best : 0;
    }

    private static double median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(mid)
                : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }
}
