package com.finance.portfolio.fixedincome;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.fixedincome.bond.BondCouponEventBuilder;
import com.finance.portfolio.fixedincome.bond.BondHolding;
import com.finance.portfolio.fixedincome.bond.BondHoldingRepository;
import com.finance.portfolio.fixedincome.deposit.DepositAccrualService;
import com.finance.portfolio.fixedincome.deposit.DepositHolding;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingRepository;
import com.finance.shared.model.CandlePeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Builds the day-by-day TRY value series for the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine
 * bond) view. Split out from {@link FixedIncomeSummaryService} (which owns the headline totals) because the
 * time-series is a distinct read concern — a per-date forward-filled day loop — while both share the valuation
 * primitives in {@link FixedIncomeValuationSupport}. Today's endpoint reconciles with the headline by valuing a
 * sold bond / closed deposit at its FROZEN realized value, exactly as {@link FixedIncomeSummaryService#summary}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FixedIncomeHistoryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DepositHoldingRepository depositHoldingRepository;
    private final BondHoldingRepository bondHoldingRepository;
    private final DepositAccrualService depositAccrualService;
    private final BondCouponEventBuilder bondCouponEventBuilder;
    private final FixedIncomeValuationSupport support;

    /**
     * Day-by-day TRY value of the fixed-income book over the period window {@code [from, today]}. The period
     * token (1M/6M/1Y/3Y/5Y/ALL) is resolved through the shared {@link CandlePeriod} resolver — the same one
     * the portfolio chart endpoints use — so the view's window matches the rest of the app (and {@code ALL}
     * anchors to the earliest holding rather than the Unix epoch, keeping the series tight).
     *
     * <p>Each point sums the deposit value over deposits whose {@code startDate <= date} and the bond value
     * over bonds whose {@code entryDate <= date} (a holding is 0 before it comes online). A sold bond and a
     * closed deposit both keep contributing their FROZEN realized value after their realization date, so the
     * chart's today endpoint reconciles with {@link FixedIncomeSummaryService#summary}. Deposit accrual is pure
     * math; bond clean prices are preloaded once per ISIN and forward-filled in memory, so the day loop issues
     * no per-date DB query.
     *
     * @throws ResourceNotFoundException if the portfolio is not owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<FixedIncomeHistoryPoint> history(Long portfolioId, String userSub, String period) {
        support.requireOwnedPortfolio(portfolioId, userSub);
        LocalDate today = LocalDate.now();

        List<DepositHolding> deposits = depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(portfolioId);
        List<BondHolding> bonds = bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId);

        LocalDate from = resolveFrom(period, deposits, bonds, today);
        // Preload each bond's full clean-price history ONCE into a per-ISIN sorted map, then forward-fill in
        // memory via floorEntry per date — instead of one uncached repository hit per (bond × calendar day),
        // which on an ALL window over an old holding would be thousands of identical-shaped round-trips.
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondPriceSeries = support.loadBondPriceSeries(bonds);
        // Resolve each holding's market bond once so the day loop can value a DISCOUNT bill by pull-to-par
        // accretion (the same way live valuation does), instead of forward-filling its sparse scraped quote.
        Map<Long, Bond> bondCatalog = support.loadBondCatalog(bonds);
        // Precompute each bond's realized coupon cashflows ONCE (couponDate → TRY amount), each priced at the
        // per-period rate in effect on its own date — the same backend coupon engine the grid uses — so the day
        // loop can credit cumulative coupons by date without re-deriving the schedule per calendar day.
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondCouponEvents =
                bondCouponEventBuilder.loadBondCouponEvents(bonds, bondCatalog, bondPriceSeries);
        // Forward-fill the last successfully-converted TRY value PER deposit so a single missing FX date (a long
        // FX gap, or an ALL window opening before the pair's series) degrades just that one point instead of
        // failing the whole series — mirroring CurrencyConverter.convertSeries rather than convertAtDate.
        Map<Long, BigDecimal> lastDepositTry = new HashMap<>();
        // A deposit's cost (principal FX→TRY at its own start date) is constant across dates — resolve it once
        // rather than re-converting per calendar day.
        Map<Long, BigDecimal> depositCostById = new HashMap<>();
        for (DepositHolding d : deposits) {
            try {
                depositCostById.put(d.getId(), support.depositCostTry(d));
            } catch (FxRateUnavailableException ex) {
                // No FX rate at the deposit's start date — omit its cost from the series rather than failing it,
                // mirroring the value leg's forward-fill degradation for the same gap.
                log.debug("No FX rate for deposit {} cost at {} — omitting from cost series",
                        d.getId(), d.getStartDate());
                depositCostById.put(d.getId(), null);
            }
        }
        List<FixedIncomeHistoryPoint> points = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            BigDecimal depositValue = BigDecimal.ZERO;
            // Cumulative cost basis of every holding online on this date — paired with the value it draws the K/Z
            // (value − cost) curve, and its day-over-day increments let the client grow cost by CPI for an
            // inflation-breakeven line.
            BigDecimal cost = BigDecimal.ZERO;
            for (DepositHolding d : deposits) {
                if (d.getStartDate().isAfter(date)) {
                    continue;
                }
                depositValue = depositValue.add(depositValueAt(d, date, lastDepositTry));
                BigDecimal depositCost = depositCostById.get(d.getId());
                if (depositCost != null) {
                    cost = cost.add(depositCost);
                }
            }
            BigDecimal bondValue = BigDecimal.ZERO;
            // Cumulative coupon cash a holder had received by this date — drops in bond value on an ex-coupon date
            // are offset by this on the K/Z curve so the line doesn't dip on every payment.
            BigDecimal bondCoupons = BigDecimal.ZERO;
            for (BondHolding b : bonds) {
                if (b.getEntryDate().isAfter(date)) {
                    continue;
                }
                bondValue = bondValue.add(bondValueAt(b, date, bondPriceSeries, bondCatalog));
                cost = cost.add(b.entryValue(support.isPerUnit(bondCatalog.get(b.getId()))));
                NavigableMap<LocalDate, BigDecimal> events = b.getId() == null ? null : bondCouponEvents.get(b.getId());
                if (events != null) {
                    for (BigDecimal amount : events.headMap(date, true).values()) {
                        bondCoupons = bondCoupons.add(amount);
                    }
                }
            }
            depositValue = FixedIncomeValuationSupport.scaled(depositValue);
            bondValue = FixedIncomeValuationSupport.scaled(bondValue);
            points.add(new FixedIncomeHistoryPoint(date, depositValue, bondValue,
                    FixedIncomeValuationSupport.scaled(depositValue.add(bondValue)),
                    FixedIncomeValuationSupport.scaled(cost), FixedIncomeValuationSupport.scaled(bondCoupons)));
        }
        return points;
    }

    /**
     * Per-date TRY value of a bond for the history series. A sold bond accrues live up to the day BEFORE its exit
     * date, then holds its FROZEN realized proceeds ({@code exitPrice}) from the exit date onward — the bond was
     * sold at {@code exitPrice} on that day, so the exit-day value IS the realized proceeds (not the live quote).
     * This mirrors the closed-deposit treatment in {@link #depositValueAt} and makes the chart's today endpoint
     * reconcile with {@link FixedIncomeSummaryService#summary} for a bond sold today. An open bond is forward-filled
     * from the preloaded clean-price series (falling back to its {@code entryPrice} when the date predates the
     * series), matching {@code BondValuationService#cleanPriceTry}.
     */
    private BigDecimal bondValueAt(BondHolding bond, LocalDate date,
                                   Map<Long, NavigableMap<LocalDate, BigDecimal>> seriesByBondId,
                                   Map<Long, Bond> catalog) {
        Bond catalogBond = bond.getId() == null ? null : catalog.get(bond.getId());
        boolean perUnit = support.isPerUnit(catalogBond);
        if (bond.isClosed() && !date.isBefore(bond.getExitDate())) {
            return bond.currentValue(bond.getExitPrice(), perUnit);
        }
        if (catalogBond != null) {
            BondType type = catalogBond.getBondType();
            LocalDate maturityEnd = catalogBond.getMaturityEnd();
            boolean cpiLinked = type != null && type.isCpiLinked();
            boolean goldLinked = type != null && type.isGoldLinked();
            // Settlement: at/after maturity a plain TRY bond is redeemed at PAR (100). A CPI bond redeems at its
            // indexed value and a gold bond at its gold-content value (both carried by the price history), so
            // neither settles at par — they keep the forward-filled price path below.
            if (!cpiLinked && !goldLinked && maturityEnd != null && !date.isBefore(maturityEnd)) {
                return bond.currentValue(HUNDRED, perUnit);
            }
            // A discount bill carries no coupon and its whole return is the price reaching par: accrete it
            // straight-line entry→100 per date so the chart climbs to par and its today endpoint equals the
            // live summary (which values discount bills the same way via BondValuationService).
            if (type == BondType.DISCOUNTED) {
                return bond.currentValue(bond.accretedCleanPrice(maturityEnd, date), perUnit);
            }
        }
        NavigableMap<LocalDate, BigDecimal> series = bond.getId() == null ? null : seriesByBondId.get(bond.getId());
        BigDecimal cleanPrice = null;
        if (series != null) {
            Map.Entry<LocalDate, BigDecimal> floor = series.floorEntry(date);
            if (floor != null) {
                cleanPrice = floor.getValue();
            }
        }
        if (cleanPrice == null) {
            cleanPrice = bond.getEntryPrice();
        }
        return bond.currentValue(cleanPrice, perUnit);
    }

    /**
     * Per-date TRY value of a deposit for the history series. A closed deposit accrues day-by-day like an
     * active one up to (and including) its close date, then holds its frozen realized value afterwards — so the
     * line follows the real accrual curve instead of a flat horizontal at the close value across every past day.
     * A missing FX rate on {@code date} carries forward the deposit's last converted value (or 0 before any).
     */
    private BigDecimal depositValueAt(DepositHolding deposit, LocalDate date, Map<Long, BigDecimal> lastTry) {
        boolean frozen = !deposit.isActive() && deposit.getClosedDate() != null
                && date.isAfter(deposit.getClosedDate());
        try {
            BigDecimal value = frozen
                    ? deposit.getClosedValueTry()
                    : support.toTry(depositAccrualService.accruedValue(deposit, date), deposit.getCurrency(), date);
            value = value == null ? BigDecimal.ZERO : value;
            if (deposit.getId() != null) {
                lastTry.put(deposit.getId(), value);
            }
            return value;
        } catch (FxRateUnavailableException ex) {
            log.debug("No FX rate for deposit {} on {} — carrying forward last converted value",
                    deposit.getId(), date);
            BigDecimal carried = deposit.getId() == null ? null : lastTry.get(deposit.getId());
            return carried == null ? BigDecimal.ZERO : carried;
        }
    }

    /**
     * Window start for the period. For a bounded token (1M..5Y) it is {@code today − period} via the shared
     * resolver; {@code ALL} clamps to the earliest holding (start/entry) date so the series does not stretch
     * back to the Unix epoch over empty days. An empty book falls back to today (a single trivial point).
     */
    private LocalDate resolveFrom(String period, List<DepositHolding> deposits, List<BondHolding> bonds,
                                  LocalDate today) {
        LocalDate earliest = earliestHolding(deposits, bonds, today);
        CandlePeriod candlePeriod = CandlePeriod.fromCode(period);
        if (candlePeriod == CandlePeriod.ALL) {
            return earliest;
        }
        // Clamp every bounded window (1M..5Y) to the earliest holding too: a young book must never render a flat
        // pre-entry run before any deposit/bond existed — the chart starts the day the first holding came online.
        LocalDate periodStart = candlePeriod.toStartDateTime(today.atStartOfDay()).toLocalDate();
        return periodStart.isBefore(earliest) ? earliest : periodStart;
    }

    /** Earliest deposit start / bond entry date across the book, or {@code today} for an empty book. */
    private LocalDate earliestHolding(List<DepositHolding> deposits, List<BondHolding> bonds, LocalDate today) {
        LocalDate earliest = today;
        for (DepositHolding d : deposits) {
            if (d.getStartDate().isBefore(earliest)) earliest = d.getStartDate();
        }
        for (BondHolding b : bonds) {
            if (b.getEntryDate().isBefore(earliest)) earliest = b.getEntryDate();
        }
        return earliest;
    }
}
