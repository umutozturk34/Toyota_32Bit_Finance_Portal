package com.finance.portfolio.fixedincome.bond;

import com.finance.portfolio.model.MoneyScale;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Pure-math accrued-coupon engine for a bond/bill holding. Given the holding's ANNUAL coupon rate (percent of
 * nominal), its {@link CouponFrequency}, the bond's issue/maturity dates and an {@code asOf} date, it returns the
 * straight-line accrued coupon since the last coupon date (the "işlemiş kupon" component of the dirty price) plus
 * the daily accrual rate, both quoted per 100 nominal to match the clean price.
 *
 * <p>Accrual is the standard ACT/ACT-within-period straight line: {@code couponPerPeriod × daysSinceLastCoupon /
 * daysInPeriod}, where {@code couponPerPeriod = annualRate / paymentsPerYear} and coupon dates step from the
 * bond's issue date ({@code maturityStart}) by {@link CouponFrequency#stepMonths()}. Accrual freezes at maturity
 * and is zero before issue or for a zero-coupon bill — never throwing, so a missing/odd term degrades to no
 * accrued coupon rather than breaking the holding's valuation.
 */
@Service
public class BondCouponService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Accrued coupon for the holding as of {@code asOf}, plus the per-day accrual and the surrounding coupon
     * dates. All monetary figures are per 100 nominal.
     */
    public CouponAccrual accrued(BigDecimal annualRatePer100, CouponFrequency frequency,
                                 LocalDate maturityStart, LocalDate maturityEnd, LocalDate asOf) {
        if (annualRatePer100 == null || annualRatePer100.signum() <= 0
                || frequency == null || !frequency.paysCoupon() || maturityStart == null || asOf == null) {
            return CouponAccrual.none();
        }
        // Coupon stops accruing once the bond matures: clamp the as-of date to maturity end so a stale "today"
        // never accrues coupon past redemption.
        LocalDate effAsOf = (maturityEnd != null && asOf.isAfter(maturityEnd)) ? maturityEnd : asOf;
        if (effAsOf.isBefore(maturityStart)) {
            return CouponAccrual.none();
        }

        int stepMonths = frequency.stepMonths();
        BigDecimal couponPerPeriod = annualRatePer100.divide(
                BigDecimal.valueOf(frequency.paymentsPerYear()), MathContext.DECIMAL64);

        LocalDate last = maturityStart;
        LocalDate next = last.plusMonths(stepMonths);
        int guard = 0;
        while (!next.isAfter(effAsOf) && guard < 2000) {
            last = next;
            next = next.plusMonths(stepMonths);
            guard++;
        }
        if (maturityEnd != null && next.isAfter(maturityEnd)) {
            next = maturityEnd;
        }

        long daysSince = Math.max(0L, ChronoUnit.DAYS.between(last, effAsOf));
        long daysInPeriod = Math.max(1L, ChronoUnit.DAYS.between(last, next));

        BigDecimal fraction = BigDecimal.valueOf(daysSince)
                .divide(BigDecimal.valueOf(daysInPeriod), MathContext.DECIMAL64);
        BigDecimal accruedPer100 = couponPerPeriod.multiply(fraction, MathContext.DECIMAL64)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal dailyPer100 = couponPerPeriod
                .divide(BigDecimal.valueOf(daysInPeriod), MoneyScale.PRICE, RoundingMode.HALF_UP);

        return new CouponAccrual(accruedPer100, dailyPer100, last, next);
    }

    /**
     * The full coupons PAID while the holding was held: every coupon date strictly after {@code fromDate} (the
     * entry date) up to and including {@code asOf} (today for an open holding, the exit date for a closed one),
     * each paying {@code annualRate / paymentsPerYear} per 100 nominal. This is the realized coupon income —
     * counted even for a back-dated entry whose coupon dates already passed — separate from the still-accruing
     * partial coupon of the current period ({@link #accrued}). Zero when no coupon date falls in the window
     * (e.g. sold before its first coupon) or for a zero-coupon bill.
     */
    public CouponsPaid couponsPaid(BigDecimal annualRatePer100, CouponFrequency frequency,
                                   LocalDate maturityStart, LocalDate maturityEnd,
                                   LocalDate fromDate, LocalDate asOf) {
        if (annualRatePer100 == null || annualRatePer100.signum() <= 0
                || frequency == null || !frequency.paysCoupon()
                || maturityStart == null || fromDate == null || asOf == null) {
            return CouponsPaid.none();
        }
        LocalDate effAsOf = (maturityEnd != null && asOf.isAfter(maturityEnd)) ? maturityEnd : asOf;
        if (!effAsOf.isAfter(fromDate)) {
            return CouponsPaid.none();
        }
        int stepMonths = frequency.stepMonths();
        BigDecimal couponPerPeriod = annualRatePer100.divide(
                BigDecimal.valueOf(frequency.paymentsPerYear()), MathContext.DECIMAL64);

        int count = 0;
        LocalDate couponDate = maturityStart.plusMonths(stepMonths);
        int guard = 0;
        while (!couponDate.isAfter(effAsOf) && guard < 2000) {
            if (couponDate.isAfter(fromDate)) {
                count++;
            }
            couponDate = couponDate.plusMonths(stepMonths);
            guard++;
        }
        BigDecimal totalPer100 = couponPerPeriod.multiply(BigDecimal.valueOf(count))
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        return new CouponsPaid(count, totalPer100);
    }

    /**
     * History-aware accrued coupon for a FLOATING bond (TLREF/auction). Identical to {@link #accrued} except the
     * current period's per-100 coupon is the published PER-PERIOD rate (.ORAN) IN EFFECT at the last coupon date,
     * resolved from {@code perPeriodRateByDate} (a publication-date → per-period-rate map built from the bond's
     * coupon-rate history) — a floater resets each period, so a single static rate would mis-state every period but
     * the current one. {@code fallbackPerPeriod} is used when no history exists on/before a coupon date. Picking up
     * the latest history entry per call means a future rate reset reflects automatically.
     */
    public CouponAccrual accruedFloating(NavigableMap<LocalDate, BigDecimal> perPeriodRateByDate,
                                         BigDecimal fallbackPerPeriod, CouponFrequency frequency,
                                         LocalDate maturityStart, LocalDate maturityEnd, LocalDate asOf) {
        if (frequency == null || !frequency.paysCoupon() || maturityStart == null || asOf == null) {
            return CouponAccrual.none();
        }
        LocalDate effAsOf = (maturityEnd != null && asOf.isAfter(maturityEnd)) ? maturityEnd : asOf;
        if (effAsOf.isBefore(maturityStart)) {
            return CouponAccrual.none();
        }
        int stepMonths = frequency.stepMonths();
        LocalDate last = maturityStart;
        LocalDate next = last.plusMonths(stepMonths);
        int guard = 0;
        while (!next.isAfter(effAsOf) && guard < 2000) {
            last = next;
            next = next.plusMonths(stepMonths);
            guard++;
        }
        if (maturityEnd != null && next.isAfter(maturityEnd)) {
            next = maturityEnd;
        }
        BigDecimal couponPerPeriod = rateAt(perPeriodRateByDate, last, fallbackPerPeriod);
        if (couponPerPeriod == null || couponPerPeriod.signum() <= 0) {
            return CouponAccrual.none();
        }
        long daysSince = Math.max(0L, ChronoUnit.DAYS.between(last, effAsOf));
        long daysInPeriod = Math.max(1L, ChronoUnit.DAYS.between(last, next));
        BigDecimal fraction = BigDecimal.valueOf(daysSince)
                .divide(BigDecimal.valueOf(daysInPeriod), MathContext.DECIMAL64);
        BigDecimal accruedPer100 = couponPerPeriod.multiply(fraction, MathContext.DECIMAL64)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal dailyPer100 = couponPerPeriod
                .divide(BigDecimal.valueOf(daysInPeriod), MoneyScale.PRICE, RoundingMode.HALF_UP);
        return new CouponAccrual(accruedPer100, dailyPer100, last, next);
    }

    /**
     * History-aware realized coupons for a FLOATING bond: like {@link #couponsPaid} but each past coupon pays the
     * PER-PERIOD rate (.ORAN) that was in effect on its own payment date (resolved from {@code perPeriodRateByDate}),
     * so a TLREF/auction floater's varying resets are summed at their actual rates rather than one flat rate.
     */
    public CouponsPaid couponsPaidFloating(NavigableMap<LocalDate, BigDecimal> perPeriodRateByDate,
                                           BigDecimal fallbackPerPeriod, CouponFrequency frequency,
                                           LocalDate maturityStart, LocalDate maturityEnd,
                                           LocalDate fromDate, LocalDate asOf) {
        if (frequency == null || !frequency.paysCoupon()
                || maturityStart == null || fromDate == null || asOf == null) {
            return CouponsPaid.none();
        }
        LocalDate effAsOf = (maturityEnd != null && asOf.isAfter(maturityEnd)) ? maturityEnd : asOf;
        if (!effAsOf.isAfter(fromDate)) {
            return CouponsPaid.none();
        }
        int stepMonths = frequency.stepMonths();
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        LocalDate couponDate = maturityStart.plusMonths(stepMonths);
        int guard = 0;
        while (!couponDate.isAfter(effAsOf) && guard < 2000) {
            if (couponDate.isAfter(fromDate)) {
                BigDecimal rate = rateAt(perPeriodRateByDate, couponDate, fallbackPerPeriod);
                if (rate != null && rate.signum() > 0) {
                    count++;
                    total = total.add(rate);
                }
            }
            couponDate = couponDate.plusMonths(stepMonths);
            guard++;
        }
        return new CouponsPaid(count, total.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP));
    }

    /**
     * The full coupon schedule (every coupon date from issue to maturity), each priced at the PER-PERIOD rate
     * (.ORAN) in effect on its own payment date — resolved from {@code perPeriodRateByDate} so a floater's coupons
     * carry their historical resets, not one flat rate (a fixed bond's flat/empty map just repeats the fallback).
     * Each entry is tagged relative to {@code entryDate} and {@code asOf}: {@code BEFORE_ENTRY} (paid before the
     * holder bought), {@code RECEIVED} (paid while held), or {@code UPCOMING}. This shares the exact coupon-date
     * stepping of {@link #couponsPaidFloating}/{@link #couponsPaid}, so the schedule reconciles with the totals.
     */
    public List<ScheduleEntry> schedule(NavigableMap<LocalDate, BigDecimal> perPeriodRateByDate,
                                        BigDecimal fallbackPerPeriod, CouponFrequency frequency,
                                        LocalDate maturityStart, LocalDate maturityEnd,
                                        LocalDate entryDate, LocalDate asOf) {
        List<ScheduleEntry> out = new ArrayList<>();
        if (frequency == null || !frequency.paysCoupon() || maturityStart == null || maturityEnd == null) {
            return out;
        }
        int stepMonths = frequency.stepMonths();
        LocalDate couponDate = maturityStart.plusMonths(stepMonths);
        int guard = 0;
        while (!couponDate.isAfter(maturityEnd) && guard < 2000) {
            BigDecimal rate = rateAt(perPeriodRateByDate, couponDate, fallbackPerPeriod);
            if (rate != null && rate.signum() > 0) {
                String status;
                if (entryDate != null && !couponDate.isAfter(entryDate)) {
                    status = "BEFORE_ENTRY";
                } else if (asOf != null && !couponDate.isAfter(asOf)) {
                    status = "RECEIVED";
                } else {
                    status = "UPCOMING";
                }
                out.add(new ScheduleEntry(couponDate, rate.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP), status));
            }
            couponDate = couponDate.plusMonths(stepMonths);
            guard++;
        }
        return out;
    }

    /** One coupon in a bond's schedule: its payment {@code date}, the per-100 rate that priced it, and its status. */
    public record ScheduleEntry(LocalDate date, BigDecimal ratePer100, String status) {
    }

    /** The per-period rate in effect on {@code date} (latest publication on/before it), else {@code fallback}. */
    private static BigDecimal rateAt(NavigableMap<LocalDate, BigDecimal> rateByDate,
                                     LocalDate date, BigDecimal fallback) {
        if (rateByDate != null) {
            Map.Entry<LocalDate, BigDecimal> entry = rateByDate.floorEntry(date);
            if (entry != null && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    /** Converts a per-100-nominal figure to a full-position TRY amount for the given nominal {@code quantity}. */
    public BigDecimal per100ToTry(BigDecimal per100, BigDecimal quantity) {
        if (per100 == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return per100.multiply(quantity).divide(HUNDRED, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /**
     * Accrued-coupon result, all per 100 nominal. {@code accruedPer100} is the işlemiş kupon to add to the clean
     * price for the dirty value; {@code dailyAccrualPer100} is how much coupon accrues per calendar day in the
     * current period; {@code lastCouponDate}/{@code nextCouponDate} bracket {@code asOf}.
     */
    public record CouponAccrual(BigDecimal accruedPer100, BigDecimal dailyAccrualPer100,
                                LocalDate lastCouponDate, LocalDate nextCouponDate) {

        /** A no-accrual result (zero-coupon bill, pre-issue, or missing terms). */
        public static CouponAccrual none() {
            return new CouponAccrual(BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }
    }

    /**
     * Realized coupon income while the holding was held: {@code count} full coupon payments totalling
     * {@code totalPer100} (per 100 nominal). Zero when no coupon date fell inside the holding window.
     */
    public record CouponsPaid(int count, BigDecimal totalPer100) {

        /** No coupons paid (window too short, sold pre-first-coupon, or zero-coupon bill). */
        public static CouponsPaid none() {
            return new CouponsPaid(0, BigDecimal.ZERO);
        }
    }
}
