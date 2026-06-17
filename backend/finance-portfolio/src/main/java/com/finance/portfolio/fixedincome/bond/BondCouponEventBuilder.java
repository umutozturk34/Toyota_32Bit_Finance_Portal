package com.finance.portfolio.fixedincome.bond;

import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Builds each bond holding's realized-coupon cashflow timeline ({@code couponDate → TRY amount}) for the fixed-income
 * history series, extracted from {@code FixedIncomeSummaryService} so coupon-schedule modelling (floater reset rates,
 * indexed coupon bases, and ex-coupon drop snapping) is its own cohesive unit. Takes the already-loaded bond catalog
 * and clean-price series as input; depends only on the coupon engine and the rate-history repository.
 */
@Component
@RequiredArgsConstructor
public class BondCouponEventBuilder {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    // A coupon-sized one-day price fall (%) below which we treat a move as the ex-coupon drop of a dirty-price quote.
    private static final BigDecimal EX_COUPON_DROP_PCT = new BigDecimal("-1");

    private final BondCouponService bondCouponService;
    private final BondRateHistoryRepository bondRateHistoryRepository;

    /**
     * Precomputes each bond's realized coupon cashflows ({@code couponDate → TRY amount}), each priced at the
     * per-period rate in effect on its own date — so a day loop can credit cumulative coupons by date without
     * re-deriving the schedule per calendar day. A coupon is credited on the day the price ACTUALLY drops (the
     * ex-coupon date) so a dirty-price series doesn't leave a one-day dip on the value-plus-coupons line.
     */
    public Map<Long, NavigableMap<LocalDate, BigDecimal>> loadBondCouponEvents(List<BondHolding> bonds,
            Map<Long, Bond> catalog, Map<Long, NavigableMap<LocalDate, BigDecimal>> priceSeriesByBondId) {
        Map<Long, NavigableMap<LocalDate, BigDecimal>> eventsByBondId = new HashMap<>();
        for (BondHolding bond : bonds) {
            NavigableMap<LocalDate, BigDecimal> events = new TreeMap<>();
            Bond catalogBond = bond.getId() == null ? null : catalog.get(bond.getId());
            BondType type = catalogBond == null ? null : catalogBond.getBondType();
            if (catalogBond == null || type == null || type == BondType.DISCOUNTED) {
                if (bond.getId() != null) {
                    eventsByBondId.put(bond.getId(), events);
                }
                continue;
            }
            CouponFrequency frequency = bond.getCouponPaymentFrequency();
            boolean floating = type.isParFloater() && bond.getCouponRateOverride() == null;
            NavigableMap<LocalDate, BigDecimal> rateMap = floating ? loadCouponRateHistory(bond.getBondIsin()) : null;
            BigDecimal fallbackPerPeriod = (bond.getCouponRateOverride() != null
                    && frequency != null && frequency.paysCoupon())
                    ? bond.getCouponRateOverride().divide(
                            BigDecimal.valueOf(frequency.paymentsPerYear()), MoneyScale.PRICE, RoundingMode.HALF_UP)
                    : catalogBond.getCouponRate();
            // A CPI/gold coupon rides the indexed/gold value, which changes over time, so each coupon is converted
            // against that value AT ITS OWN DATE (from the forward-filled price series); a plain/floater coupon is
            // on the constant face nominal.
            boolean indexed = type.isCpiLinked() || type.isGoldLinked();
            boolean perUnit = type.isPerUnit();
            NavigableMap<LocalDate, BigDecimal> priceSeries = bond.getId() == null ? null : priceSeriesByBondId.get(bond.getId());
            // Coupons stop being received at the realization cutoff: the exit date for a sold bond (the buyer takes
            // later coupons), else maturity. schedule() then tags every coupon ≤ cutoff and > entry as RECEIVED.
            LocalDate cutoff = bond.isClosed() ? bond.getExitDate() : catalogBond.getMaturityEnd();
            for (BondCouponService.ScheduleEntry e : bondCouponService.schedule(rateMap, fallbackPerPeriod, frequency,
                    catalogBond.getMaturityStart(), catalogBond.getMaturityEnd(), bond.getEntryDate(), cutoff, priceSeries)) {
                if ("RECEIVED".equals(e.status())) {
                    BigDecimal couponBase = indexed
                            ? bond.currentValue(priceAt(priceSeries, e.date(), bond.getEntryPrice()), perUnit)
                            : bond.getQuantity().multiply(BigDecimal.valueOf(100));
                    LocalDate creditDate = snapToExCouponDrop(priceSeries, e.date());
                    events.merge(creditDate, bondCouponService.per100ToTry(e.ratePer100(), couponBase), BigDecimal::add);
                }
            }
            eventsByBondId.put(bond.getId(), events);
        }
        return eventsByBondId;
    }

    /**
     * Returns the ex-coupon DROP date near a scheduled coupon date for a dirty-price series, else the scheduled date.
     * Scans observations in {@code [scheduled-7, scheduled]} for the single largest day-over-day fall; if that fall
     * clears {@link #EX_COUPON_DROP_PCT} it is the day the quote shed its accrued interest, so the coupon credit is
     * snapped there to keep the value-plus-coupons line flat. The window stops AT the scheduled date — never after —
     * so a coupon is never credited later than its date (which would also push a just-received coupon past
     * {@code today} and drop it from the received total). A clean series (no such fall) is left on its date.
     */
    private LocalDate snapToExCouponDrop(NavigableMap<LocalDate, BigDecimal> series, LocalDate scheduled) {
        if (series == null || series.isEmpty()) {
            return scheduled;
        }
        NavigableMap<LocalDate, BigDecimal> window =
                series.subMap(scheduled.minusDays(7), true, scheduled, true);
        LocalDate dropDate = null;
        BigDecimal worst = BigDecimal.ZERO;
        BigDecimal prev = null;
        for (Map.Entry<LocalDate, BigDecimal> en : window.entrySet()) {
            if (prev != null && prev.signum() > 0 && en.getValue() != null) {
                BigDecimal chgPct = en.getValue().subtract(prev)
                        .multiply(HUNDRED).divide(prev, MoneyScale.PRICE, RoundingMode.HALF_UP);
                if (chgPct.compareTo(worst) < 0) {
                    worst = chgPct;
                    dropDate = en.getKey();
                }
            }
            prev = en.getValue();
        }
        return (dropDate != null && worst.compareTo(EX_COUPON_DROP_PCT) < 0) ? dropDate : scheduled;
    }

    /** Forward-filled price at {@code date} from the series (latest on/before), falling back to {@code fallback}. */
    private BigDecimal priceAt(NavigableMap<LocalDate, BigDecimal> series, LocalDate date, BigDecimal fallback) {
        if (series != null) {
            Map.Entry<LocalDate, BigDecimal> floor = series.floorEntry(date);
            if (floor != null && floor.getValue() != null) {
                return floor.getValue();
            }
        }
        return fallback;
    }

    /**
     * The bond's published per-period coupon rate (.ORAN) over time keyed by publication date — a floater's reset
     * history. Rows with a null coupon (e.g. a discount bill) are skipped; empty when the ISIN is missing.
     */
    private NavigableMap<LocalDate, BigDecimal> loadCouponRateHistory(String isin) {
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
        if (isin == null || isin.isBlank()) {
            return rates;
        }
        for (BondRateHistory row : bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isin)) {
            if (row.getCouponRate() != null) {
                rates.put(row.getRateDate(), row.getCouponRate());
            }
        }
        return rates;
    }
}
