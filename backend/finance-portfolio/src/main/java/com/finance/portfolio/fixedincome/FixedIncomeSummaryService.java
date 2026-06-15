package com.finance.portfolio.fixedincome;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import com.finance.portfolio.dto.response.FixedIncomeHistoryPoint;
import com.finance.portfolio.dto.response.FixedIncomeSummaryResponse;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondType;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.bond.repository.BondRepository;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.portfolio.fixedincome.bond.BondCouponService;
import com.finance.portfolio.fixedincome.bond.BondHolding;
import com.finance.portfolio.fixedincome.bond.BondHoldingRepository;
import com.finance.portfolio.fixedincome.bond.CouponFrequency;
import com.finance.portfolio.fixedincome.bond.BondValuationService;
import com.finance.portfolio.fixedincome.deposit.DepositAccrualService;
import com.finance.portfolio.fixedincome.deposit.DepositHolding;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingRepository;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.model.CandlePeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Read-side aggregate dedicated to the standalone "Mevduat &amp; Tahvil" (deposit + Türkiye Hazine bond) view:
 * the headline totals + per-kind allocation, and a value-over-time series. This is deliberately SEPARATE from
 * the spot/VIOP {@code PortfolioSummaryService} — fixed-income holdings live in their own tables and are valued
 * by their own services, so folding them into the spot snapshot core would conflate two different products.
 *
 * <p>Ownership is non-negotiable: every public method first resolves the portfolio via
 * {@link PortfolioRepository#findByIdAndUserSub} (404 if the caller doesn't own it) BEFORE any holding is
 * loaded, so a portfolio id alone can never leak another user's fixed-income book.
 *
 * <p>Currency: bonds are ALWAYS TRY. Deposits accrue in their own currency, so an active deposit's value is
 * FX-converted to TRY at the as-of date and its cost (principal) at its entry (start) date — reusing the SAME
 * {@link CurrencyConverter#convertAtDate} the deposit response uses, so the headline reconciles with the grid.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FixedIncomeSummaryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioRepository portfolioRepository;
    private final DepositHoldingRepository depositHoldingRepository;
    private final BondHoldingRepository bondHoldingRepository;
    private final DepositAccrualService depositAccrualService;
    private final BondValuationService bondValuationService;
    private final BondCouponService bondCouponService;
    private final BondRateHistoryRepository bondRateHistoryRepository;
    private final BondRepository bondRepository;
    private final CurrencyConverter currencyConverter;

    /**
     * Headline TRY figures for the view as of today: total cost/value/PnL, the per-kind value split and
     * counts. Cost is the deposit principal (FX→TRY at entry) plus bond entry value; value is the
     * accrued/realized deposit value (FX→TRY at today for an active foreign deposit) plus bond clean-price
     * valuation. {@code pnlPercent} is null when cost is zero.
     *
     * @throws ResourceNotFoundException if the portfolio is not owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public FixedIncomeSummaryResponse summary(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        LocalDate today = LocalDate.now();

        List<DepositHolding> deposits = depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(portfolioId);
        List<BondHolding> bonds = bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId);

        BigDecimal depositValue = BigDecimal.ZERO;
        BigDecimal depositCost = BigDecimal.ZERO;
        for (DepositHolding d : deposits) {
            // Mirror history()'s per-holding FX-gap tolerance: a foreign deposit whose value (today's rate) or
            // cost (its start-date rate) has no FX observation must degrade just that one leg to zero, NOT 500 the
            // whole headline. The two legs convert at DIFFERENT dates, so each is guarded independently.
            try {
                depositValue = depositValue.add(depositValueTry(d, today));
            } catch (FxRateUnavailableException ex) {
                log.debug("No FX rate for deposit {} value at {} — omitting from headline value", d.getId(), today);
            }
            try {
                depositCost = depositCost.add(depositCostTry(d));
            } catch (FxRateUnavailableException ex) {
                log.debug("No FX rate for deposit {} cost at {} — omitting from headline cost",
                        d.getId(), d.getStartDate());
            }
        }

        BigDecimal bondValue = BigDecimal.ZERO;
        BigDecimal bondCost = BigDecimal.ZERO;
        for (BondHolding b : bonds) {
            // A sold bond is valued at its FROZEN exit price (its realized proceeds), NOT today's live clean
            // price — otherwise an exited position keeps drifting with the market and double-counts capital the
            // user no longer holds. This mirrors the per-row grid (BondHoldingService.toResponse), whose
            // currentValueTry is the clean (nominal) value, so the headline reconciles with the grid.
            boolean perUnit = isPerUnitBond(b.getBondSeriesCode());
            BigDecimal bondVal = b.isClosed()
                    ? b.currentValue(b.getExitPrice(), perUnit)
                    : bondValuationService.currentValueTry(b, today);
            bondValue = bondValue.add(bondVal);
            bondCost = bondCost.add(b.entryValue(perUnit));
        }

        BigDecimal totalValue = scaled(depositValue.add(bondValue));
        BigDecimal totalCost = scaled(depositCost.add(bondCost));
        BigDecimal totalPnl = scaled(totalValue.subtract(totalCost));
        BigDecimal pnlPercent = totalCost.signum() == 0 ? null
                : totalPnl.multiply(HUNDRED).divide(totalCost, MoneyScale.PRICE, RoundingMode.HALF_UP);
        // Coupon cash already received across all bonds — realized income on top of the clean-price value, summed
        // exactly the way the history series' last point credits it (so the chart endpoint and this headline agree).
        BigDecimal couponsReceived = scaled(totalBondCouponsReceived(bonds, today));

        return new FixedIncomeSummaryResponse(totalCost, totalValue, totalPnl, pnlPercent,
                deposits.size(), bonds.size(), scaled(depositValue), scaled(bondValue), couponsReceived, today);
    }

    /**
     * Cumulative TRY coupon cash received across all bonds as of {@code today}, reusing the same coupon-event
     * engine the history series builds (each past coupon priced at its own date's .ORAN, CPI/gold coupons on the
     * indexed value at the coupon date). Summing {@code headMap(today, true)} per bond yields exactly the history
     * series' last-point coupon credit, so the headline and the chart reconcile. Zero when there are no bonds.
     */
    private BigDecimal totalBondCouponsReceived(List<BondHolding> bonds, LocalDate today) {
        if (bonds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, Bond> bondCatalog = loadBondCatalog(bonds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondPriceSeries = loadBondPriceSeries(bonds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> couponEvents =
                loadBondCouponEvents(bonds, bondCatalog, bondPriceSeries);
        BigDecimal total = BigDecimal.ZERO;
        for (BondHolding b : bonds) {
            NavigableMap<LocalDate, BigDecimal> events = b.getId() == null ? null : couponEvents.get(b.getId());
            if (events == null) {
                continue;
            }
            for (BigDecimal amount : events.headMap(today, true).values()) {
                total = total.add(amount);
            }
        }
        return total;
    }

    /**
     * Day-by-day TRY value of the fixed-income book over the period window {@code [from, today]}. The period
     * token (1M/6M/1Y/3Y/5Y/ALL) is resolved through the shared {@link CandlePeriod} resolver — the same one
     * the portfolio chart endpoints use — so the view's window matches the rest of the app (and {@code ALL}
     * anchors to the earliest holding rather than the Unix epoch, keeping the series tight).
     *
     * <p>Each point sums the deposit value over deposits whose {@code startDate <= date} and the bond value
     * over bonds whose {@code entryDate <= date} (a holding is 0 before it comes online). A sold bond and a
     * closed deposit both keep contributing their FROZEN realized value after their realization date, so the
     * chart's today endpoint reconciles with {@link #summary}. Deposit accrual is pure math; bond clean prices
     * are preloaded once per ISIN and forward-filled in memory, so the day loop issues no per-date DB query.
     *
     * @throws ResourceNotFoundException if the portfolio is not owned by {@code userSub}
     */
    @Transactional(readOnly = true)
    public List<FixedIncomeHistoryPoint> history(Long portfolioId, String userSub, String period) {
        requireOwnedPortfolio(portfolioId, userSub);
        LocalDate today = LocalDate.now();

        List<DepositHolding> deposits = depositHoldingRepository.findByPortfolioIdOrderByStartDateDescIdDesc(portfolioId);
        List<BondHolding> bonds = bondHoldingRepository.findByPortfolioIdOrderByEntryDateDescIdDesc(portfolioId);

        LocalDate from = resolveFrom(period, deposits, bonds, today);
        // Preload each bond's full clean-price history ONCE into a per-ISIN sorted map, then forward-fill in
        // memory via floorEntry per date — instead of one uncached repository hit per (bond × calendar day),
        // which on an ALL window over an old holding would be thousands of identical-shaped round-trips.
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondPriceSeries = loadBondPriceSeries(bonds);
        // Resolve each holding's market bond once so the day loop can value a DISCOUNT bill by pull-to-par
        // accretion (the same way live valuation does), instead of forward-filling its sparse scraped quote.
        Map<Long, Bond> bondCatalog = loadBondCatalog(bonds);
        // Precompute each bond's realized coupon cashflows ONCE (couponDate → TRY amount), each priced at the
        // per-period rate in effect on its own date — the same backend coupon engine the grid uses — so the day
        // loop can credit cumulative coupons by date without re-deriving the schedule per calendar day.
        Map<Long, NavigableMap<LocalDate, BigDecimal>> bondCouponEvents =
                loadBondCouponEvents(bonds, bondCatalog, bondPriceSeries);
        // Forward-fill the last successfully-converted TRY value PER deposit so a single missing FX date (a long
        // FX gap, or an ALL window opening before the pair's series) degrades just that one point instead of
        // failing the whole series — mirroring CurrencyConverter.convertSeries rather than convertAtDate.
        Map<Long, BigDecimal> lastDepositTry = new HashMap<>();
        // A deposit's cost (principal FX→TRY at its own start date) is constant across dates — resolve it once
        // rather than re-converting per calendar day.
        Map<Long, BigDecimal> depositCostById = new HashMap<>();
        for (DepositHolding d : deposits) {
            try {
                depositCostById.put(d.getId(), depositCostTry(d));
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
                cost = cost.add(b.entryValue(isPerUnit(bondCatalog.get(b.getId()))));
                NavigableMap<LocalDate, BigDecimal> events = b.getId() == null ? null : bondCouponEvents.get(b.getId());
                if (events != null) {
                    for (BigDecimal amount : events.headMap(date, true).values()) {
                        bondCoupons = bondCoupons.add(amount);
                    }
                }
            }
            depositValue = scaled(depositValue);
            bondValue = scaled(bondValue);
            points.add(new FixedIncomeHistoryPoint(date, depositValue, bondValue,
                    scaled(depositValue.add(bondValue)), scaled(cost), scaled(bondCoupons)));
        }
        return points;
    }

    /**
     * Per-date TRY value of a bond for the history series. A sold bond accrues live up to the day BEFORE its exit
     * date, then holds its FROZEN realized proceeds ({@code exitPrice}) from the exit date onward — the bond was
     * sold at {@code exitPrice} on that day, so the exit-day value IS the realized proceeds (not the live quote).
     * This mirrors the closed-deposit treatment in {@link #depositValueAt} and makes the chart's today endpoint
     * reconcile with {@link #summary} for a bond sold today (which also values a closed bond at its exit proceeds).
     * An open bond is forward-filled from the preloaded clean-price series (falling back to its {@code entryPrice}
     * when the date predates the series), matching {@link BondValuationService#cleanPriceTry}.
     */
    private BigDecimal bondValueAt(BondHolding bond, LocalDate date,
                                   Map<Long, NavigableMap<LocalDate, BigDecimal>> seriesByBondId,
                                   Map<Long, Bond> catalog) {
        Bond catalogBond = bond.getId() == null ? null : catalog.get(bond.getId());
        boolean perUnit = isPerUnit(catalogBond);
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

    /** Whether the resolved bond is quoted per certificate (gold-linked) rather than per 100 nominal. */
    private boolean isPerUnit(Bond bond) {
        return bond != null && bond.getBondType() != null && bond.getBondType().isPerUnit();
    }

    /** Resolves the bond by series code and reports whether it is per-unit (gold-linked); false when unresolved. */
    private boolean isPerUnitBond(String seriesCode) {
        return isPerUnit(bondRepository.findById(seriesCode).orElse(null));
    }

    /**
     * Loads each bond's full clean-price history into a date-sorted map keyed by holding id, issuing one query
     * per ISIN (vs. one per date per bond in the day loop). Rows with a null price are skipped so a later
     * {@code floorEntry} never carries a null forward.
     */
    private Map<Long, NavigableMap<LocalDate, BigDecimal>> loadBondPriceSeries(List<BondHolding> bonds) {
        Map<Long, NavigableMap<LocalDate, BigDecimal>> seriesByBondId = new HashMap<>();
        for (BondHolding bond : bonds) {
            if (bond.getId() == null) {
                continue;
            }
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            String isin = bond.getBondIsin();
            if (isin != null && !isin.isBlank()) {
                bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isin).forEach(row -> {
                    if (row.getPrice() != null) {
                        series.put(row.getRateDate(), row.getPrice());
                    }
                });
            }
            seriesByBondId.put(bond.getId(), series);
        }
        return seriesByBondId;
    }

    /**
     * Resolves each holding's market {@link Bond} once, keyed by holding id, so the day loop can read the bond
     * type/maturity without a per-date lookup. A holding whose series no longer resolves is simply absent (its
     * valuation then falls back to the forward-filled price path).
     */
    private Map<Long, Bond> loadBondCatalog(List<BondHolding> bonds) {
        Map<Long, Bond> catalog = new HashMap<>();
        for (BondHolding bond : bonds) {
            if (bond.getId() == null) {
                continue;
            }
            bondRepository.findById(bond.getBondSeriesCode())
                    .ifPresent(resolved -> catalog.put(bond.getId(), resolved));
        }
        return catalog;
    }

    /**
     * Each bond's realized coupon cashflows keyed by holding id: a sorted {@code couponDate → TRY amount} map of
     * every coupon paid strictly after entry, up to the holding's realization cutoff (the exit date when sold, else
     * maturity). Each coupon is priced by {@link BondCouponService#schedule} at the per-period rate in effect on its
     * own date (a floater's historical resets). The TRY amount is taken against the right base PER coupon date:
     * the face nominal for a plain/floater bond, but the INFLATION-INDEXED value (or the GOLD value) AT THAT DATE
     * for a CPI / gold bond — so a CPI bond's small real coupon and a gold bond's rental DO get credited on the K/Z
     * curve (on the accumulated base), not dropped. Only a discount bill (no coupon) contributes nothing.
     */
    private Map<Long, NavigableMap<LocalDate, BigDecimal>> loadBondCouponEvents(List<BondHolding> bonds,
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
                    catalogBond.getMaturityStart(), catalogBond.getMaturityEnd(), bond.getEntryDate(), cutoff)) {
                if ("RECEIVED".equals(e.status())) {
                    BigDecimal couponBase = indexed
                            ? bond.currentValue(priceAt(priceSeries, e.date(), bond.getEntryPrice()), perUnit)
                            : bond.getQuantity().multiply(BigDecimal.valueOf(100));
                    events.put(e.date(), bondCouponService.per100ToTry(e.ratePer100(), couponBase));
                }
            }
            eventsByBondId.put(bond.getId(), events);
        }
        return eventsByBondId;
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
                    : toTry(depositAccrualService.accruedValue(deposit, date), deposit.getCurrency(), date);
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

    /** Active deposit value FX-converted to TRY at {@code asOf}; a closed deposit is already frozen in TRY. */
    private BigDecimal depositValueTry(DepositHolding deposit, LocalDate asOf) {
        BigDecimal valueNative = depositAccrualService.realizedOrAccruedValue(deposit, asOf);
        return deposit.isActive() ? toTry(valueNative, deposit.getCurrency(), asOf) : valueNative;
    }

    /** Cost basis = principal FX-converted to TRY at the deposit's entry (start) date. */
    private BigDecimal depositCostTry(DepositHolding deposit) {
        return toTry(deposit.getPrincipal(), deposit.getCurrency(), deposit.getStartDate());
    }

    private BigDecimal toTry(BigDecimal amount, String currencyCode, LocalDate asOf) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        Currency from = Currency.fromCode(currencyCode);
        if (from == null || from == Currency.TRY) {
            return amount;
        }
        return currencyConverter.convertAtDate(amount, from, Currency.TRY, asOf);
    }

    /** Resolves and asserts ownership; the portfolio entity itself is unused here, so nothing is returned. */
    private void requireOwnedPortfolio(Long portfolioId, String userSub) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
