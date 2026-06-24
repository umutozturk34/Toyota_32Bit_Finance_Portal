package com.finance.app.bond;

import com.finance.common.market.MarketDataReadiness;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.portfolio.fixedincome.bond.CouponFrequency;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One-shot startup diagnostic that settles the "is the EVDS DİBS price clean or dirty?" question EMPIRICALLY, per
 * bond type — instead of trusting convention. A CLEAN price does not move on the coupon date (it is smooth across
 * coupons); a DIRTY price drops by ~one coupon on the ex-coupon date (the accrued interest it carried resets to
 * zero). So for each coupon-bearing bond we measure the price change ACROSS its coupon dates and compare it to the
 * bond's ordinary daily noise: a systematic drop ≫ noise ⇒ dirty; no drop ⇒ clean. Results are aggregated by
 * {@link BondType} and logged once, so a single startup answers "which bond types are clean, which are dirty"
 * (the user's "bazıları temiz bazıları kirli"). Read-only; never throws into the boot path.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class BondPriceConventionDiagnostic {

    private static final int READINESS_MAX_CHECKS = 90;
    private static final long READINESS_CHECK_MS = 20_000;
    private static final int MIN_POINTS = 12;          // too sparse to judge below this
    private static final int MIN_COUPONS = 2;          // need at least two coupon crossings
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BondRepository bondRepository;
    private final BondRateHistoryRepository rateHistoryRepository;
    private final ObjectProvider<MarketDataReadiness> marketDataReadiness;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread worker = new Thread(this::run, "bond-price-convention-diagnostic");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        if (!awaitMarketData()) {
            log.warn("Bond price-convention diagnostic: market data not ready; skipping");
            return;
        }
        try {
            Map<BondType, TypeStat> byType = new EnumMap<>(BondType.class);
            for (Bond bond : bondRepository.findAll()) {
                BondType type = bond.getBondType();
                if (type == null || type == BondType.DISCOUNTED || bond.getMaturityStart() == null) {
                    continue; // discount bills carry no coupon, so there is nothing to drop
                }
                Verdict v = classify(bond, type);
                if (v == null) {
                    continue;
                }
                byType.computeIfAbsent(type, k -> new TypeStat()).add(v);
            }
            if (byType.isEmpty()) {
                log.info("Bond price-convention diagnostic: no coupon-bearing bond had enough history to judge");
                return;
            }
            log.info("===== Bond price-convention diagnostic (EVDS clean-vs-dirty, per type) =====");
            byType.forEach((type, stat) -> log.info("  {}: {}", type, stat.summary()));
            log.info("  Reading: DIRTY-like = price drops ~one coupon on the coupon date (carries accrued interest);"
                    + " CLEAN-like = no coupon-date drop. A type that is DIRTY-like means our 'clean price' already"
                    + " embeds accrued, so the modal's separate 'işlemiş kupon / kirli fiyat' overstates it.");
            log.info("==========================================================================");
        } catch (RuntimeException e) {
            log.warn("Bond price-convention diagnostic failed: {}", e.getMessage());
        }
    }

    /** Classifies one bond by the average price move across its coupon dates vs. its ordinary daily noise. */
    private Verdict classify(Bond bond, BondType type) {
        List<BondRateHistory> rows = rateHistoryRepository.findByIsinCodeOrderByRateDateAsc(bond.getIsinCode());
        List<Point> series = new ArrayList<>();
        for (BondRateHistory r : rows) {
            if (r.getRateDate() != null && r.getPrice() != null && r.getPrice().signum() > 0) {
                series.add(new Point(r.getRateDate(), r.getPrice()));
            }
        }
        if (series.size() < MIN_POINTS) {
            return null;
        }
        double baseline = medianAbsDailyChangePct(series);

        // Coupon dates stepped from the issue date by the type's standard frequency, clamped to the series window.
        CouponFrequency frequency = CouponFrequency.defaultFor(type);
        int step = frequency.stepMonths();
        if (step <= 0) {
            return null;
        }
        LocalDate first = series.get(0).date();
        LocalDate last = series.get(series.size() - 1).date();
        List<Double> couponChanges = new ArrayList<>();
        for (LocalDate d = bond.getMaturityStart().plusMonths(step);
                (bond.getMaturityEnd() == null || !d.isAfter(bond.getMaturityEnd())) && !d.isAfter(last);
                d = d.plusMonths(step)) {
            if (d.isBefore(first)) {
                continue;
            }
            Double chg = changeAcross(series, d);
            if (chg != null) {
                couponChanges.add(chg);
            }
        }
        if (couponChanges.size() < MIN_COUPONS) {
            return null;
        }
        double avgCouponChange = couponChanges.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        // Dirty: a clear DOWNWARD move at the coupon, well beyond ordinary noise. Clean: coupon-date move within noise.
        double dropFloor = Math.max(1.0, baseline * 3);
        boolean dirty = avgCouponChange <= -dropFloor;
        boolean clean = Math.abs(avgCouponChange) <= Math.max(0.5, baseline * 1.5);
        Verdict.Kind kind = dirty ? Verdict.Kind.DIRTY : (clean ? Verdict.Kind.CLEAN : Verdict.Kind.INCONCLUSIVE);
        return new Verdict(kind, avgCouponChange);
    }

    /** Price % change from the last observation strictly before {@code date} to the first on/after it. */
    private Double changeAcross(List<Point> series, LocalDate date) {
        Point before = null;
        Point after = null;
        for (Point p : series) {
            if (p.date().isBefore(date)) {
                before = p;
            } else {
                after = p;
                break;
            }
        }
        if (before == null || after == null || before.price().signum() <= 0) {
            return null;
        }
        return after.price().subtract(before.price())
                .multiply(HUNDRED).divide(before.price(), 6, RoundingMode.HALF_UP).doubleValue();
    }

    /** Median absolute day-over-day % change — the bond's ordinary noise floor. */
    private double medianAbsDailyChangePct(List<Point> series) {
        List<Double> moves = new ArrayList<>();
        for (int i = 1; i < series.size(); i++) {
            BigDecimal prev = series.get(i - 1).price();
            if (prev.signum() <= 0) {
                continue;
            }
            moves.add(series.get(i).price().subtract(prev)
                    .multiply(HUNDRED).divide(prev, 6, RoundingMode.HALF_UP).abs().doubleValue());
        }
        if (moves.isEmpty()) {
            return 0;
        }
        moves.sort(Double::compareTo);
        return moves.get(moves.size() / 2);
    }

    private boolean awaitMarketData() {
        MarketDataReadiness readiness = marketDataReadiness.getIfAvailable();
        if (readiness == null) {
            return true;
        }
        for (int i = 0; i < READINESS_MAX_CHECKS; i++) {
            if (readiness.isReady()) {
                return true;
            }
            try {
                Thread.sleep(READINESS_CHECK_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return readiness.isReady();
    }

    private record Point(LocalDate date, BigDecimal price) {}

    private record Verdict(Kind kind, double avgCouponChangePct) {
        enum Kind { CLEAN, DIRTY, INCONCLUSIVE }
    }

    /** Per-type tally of verdicts plus the mean coupon-date move, for a single readable log line. */
    private static final class TypeStat {
        private int clean;
        private int dirty;
        private int inconclusive;
        private double sumCouponChange;
        private int n;

        void add(Verdict v) {
            switch (v.kind()) {
                case CLEAN -> clean++;
                case DIRTY -> dirty++;
                case INCONCLUSIVE -> inconclusive++;
                default -> { /* unreachable */ }
            }
            sumCouponChange += v.avgCouponChangePct();
            n++;
        }

        String summary() {
            double avg = n == 0 ? 0 : sumCouponChange / n;
            String verdict = dirty > clean && dirty >= inconclusive ? "DIRTY-like"
                    : clean >= dirty && clean >= inconclusive ? "CLEAN-like" : "INCONCLUSIVE";
            return String.format("%d bonds → %s (clean=%d, dirty=%d, inconclusive=%d, avg coupon-date move %.2f%%)",
                    n, verdict, clean, dirty, inconclusive, avg);
        }
    }
}
