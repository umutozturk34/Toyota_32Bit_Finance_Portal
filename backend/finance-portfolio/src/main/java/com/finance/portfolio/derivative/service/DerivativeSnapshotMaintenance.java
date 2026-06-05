package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and reconciles the daily VIOP snapshot history for a derivative position. Walks each day
 * from entry to close (or today, exclusive of an open today), valuing the position at that day's
 * candle close converted to TRY via each day's historical FX rate (30-day lookback + closest-prior
 * walk), with a synthetic zero row emitted on the close day when no peer lot keeps the symbol open.
 * When a day has no usable FX rate (e.g. the first days of a USD/EUR-quoted contract before its FX
 * history starts), the line is CARRIED FORWARD flat in TRY rather than left without a row, so the
 * frontend never splines a fake 0→entry ramp ("satış öncesi 0"). Also consolidates duplicate
 * same-timestamp rows (summing values) so each instant has one row.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DerivativeSnapshotMaintenance {

    static final int FX_LOOKBACK_DAYS = 30;

    private final ViopCandleRepository candleRepository;
    private final HistoricalPricingPort historicalPricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final SnapshotCalculationService snapshotCalculator;
    private final DerivativePositionRepository derivativePositionRepository;

    /**
     * Rebuilds the position's per-day asset snapshots over its lifetime. On the close day the stored
     * close price is used verbatim (FX rate 1, since it is already TRY); other days use the candle
     * close at that day's FX rate, carrying forward the last known close/rate when a day is missing.
     * Days with NO usable FX rate carry the last computed TRY value forward flat (FX rate 1 on an
     * already-TRY seed) rather than being skipped, so an early FX gap leaves no missing rows for the
     * frontend to spline a fake 0→entry ramp through.
     */
    public void backfillSnapshots(DerivativePosition position) {
        if (position.getViopContract() == null) return;
        String symbol = position.getViopContract().getSymbol();
        LocalDate from = position.getEntryDate();
        LocalDate today = LocalDate.now();
        LocalDate closeDate = position.getCloseDate();
        LocalDate to = closeDate != null ? closeDate : today;
        if (from == null || from.isAfter(to)) return;
        log.debug("Backfilling viop snapshots positionId={} symbol={} range={}..{}",
                position.getId(), symbol, from, to);

        Map<LocalDate, BigDecimal> closeByDate = loadCandleCloses(symbol, from, to);
        String currency = position.getViopContract().resolvePriceCurrency();
        boolean needsFxConversion = currency != null && !"TRY".equalsIgnoreCase(currency);
        Map<LocalDate, BigDecimal> fxByDate = needsFxConversion
                ? historicalPricingPort.getPriceSeries(MarketType.FOREX, currency.toUpperCase(),
                        from.minusDays(FX_LOOKBACK_DAYS), to)
                : Map.of();

        BigDecimal entryFxAtOpen = needsFxConversion
                ? priorOrEarliestRate(fxByDate, from)
                : BigDecimal.ONE;
        // When the FX series has NO usable point in the lookback window for a non-TRY contract,
        // we cannot reconstruct a native fallback from the stored TRY entryPrice. lastKnown stays
        // null and the per-day loop below skips days that lack a candle close (rather than reusing
        // entryPrice as if it were native — that path was the ~30x USD-as-TRY corruption).
        BigDecimal lastKnown;
        if (!needsFxConversion) {
            lastKnown = position.getEntryPrice();
        } else if (entryFxAtOpen != null && entryFxAtOpen.signum() > 0 && position.getEntryPrice() != null) {
            lastKnown = position.getEntryPrice().divide(entryFxAtOpen, 8, RoundingMode.HALF_UP);
        } else {
            lastKnown = null;
        }
        // Initial lastFxRate also seeded from history, not ONE. ONE would silently treat native USD
        // as TRY when the very first day's lookup misses and produce snapshot rows ~30x off.
        BigDecimal lastFxRate = needsFxConversion ? entryFxAtOpen : BigDecimal.ONE;
        BigDecimal closePriceOverride = position.getClosePrice();
        List<PortfolioAssetDailySnapshot> batch = new ArrayList<>();
        PortfolioAssetDailySnapshot priorInBatch = null;
        int carriedNoFx = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean isCloseDay = closeDate != null && date.equals(closeDate);
            boolean isTodayOpen = closeDate == null && date.equals(today);
            if (isTodayOpen) continue;
            boolean useCloseOverride = isCloseDay && closePriceOverride != null;
            BigDecimal close = useCloseOverride
                    ? closePriceOverride
                    : closeByDate.getOrDefault(date, lastKnown);
            BigDecimal fxRate;
            if (useCloseOverride || !needsFxConversion) {
                if (close == null) continue;
                if (!useCloseOverride) lastKnown = close;
                fxRate = BigDecimal.ONE;
            } else {
                BigDecimal byDay = closestPriorRate(fxByDate, date);
                if (byDay != null && byDay.signum() > 0) {
                    if (close == null) continue;
                    lastKnown = close;
                    fxRate = byDay;
                    lastFxRate = fxRate;
                } else if (lastFxRate != null && lastFxRate.signum() > 0) {
                    if (close == null) continue;
                    lastKnown = close;
                    fxRate = lastFxRate;
                } else {
                    // No historical FX in the lookback AND no prior usable rate cached. Rather than
                    // SKIP the day (which left early days of a USD/EUR-quoted VIOP with no snapshot row
                    // and made the frontend spline a fake 0→entry ramp), carry the line forward FLAT in
                    // TRY. close/fxRate here are an already-TRY unit price × ONE — NOT a native price ×
                    // ONE — so this does not reintroduce the ~30x USD-as-TRY corruption.
                    close = carryForwardTryUnitPrice(position, priorInBatch);
                    if (close == null) continue;
                    fxRate = BigDecimal.ONE;
                    carriedNoFx++;
                }
            }
            LocalDateTime ts = date.atTime(LocalTime.MIDNIGHT);
            PortfolioAssetDailySnapshot snapshot = snapshotCalculator
                    .buildDerivativeAssetSnapshotAt(position.getPortfolio().getId(), position, ts, close, fxRate, priorInBatch);
            if (snapshot != null) {
                if (isCloseDay) {
                    // Value-less close-day row: keep ONLY the close-day move (dailyPnlTry); zero quantity/market/
                    // cost/pnl so isCountableViopRow == false. A COUNTABLE close-slice row gets summed into the
                    // per-symbol rowMv on aggregate rebuild — and on a PARTIAL close the open remainder (also
                    // countable) then claims the WHOLE symbol notional via equity() AND addClosedEquity books the
                    // proceeds again, so the "Tümü" TRY value DOUBLES on the sell day (VIOP-only; USD/EUR recompute
                    // live, spot routes via accumulateClosedSpot). Proceeds still reach totalValue once via
                    // addClosedEquity; the open remainder keeps its own countable row.
                    snapshot.setQuantity(BigDecimal.ZERO);
                    snapshot.setMarketValueTry(BigDecimal.ZERO);
                    snapshot.setTotalCostTry(BigDecimal.ZERO);
                    snapshot.setPnlTry(BigDecimal.ZERO);
                }
                batch.add(snapshot);
                priorInBatch = snapshot;
            }
            if (isCloseDay && !hasPeerHoldingAfter(position, date)) {
                PortfolioAssetDailySnapshot zero = buildZeroDerivativeSnapshot(
                        position, ts.plusSeconds(1), close);
                batch.add(zero);
                priorInBatch = zero;
            }
        }
        if (!batch.isEmpty()) {
            assetSnapshotRepository.saveAll(batch);
            log.info("Viop snapshot backfill complete positionId={} symbol={} rows={} carriedNoFx={}",
                    position.getId(), symbol, batch.size(), carriedNoFx);
        } else if (carriedNoFx > 0) {
            log.warn("Viop snapshot backfill produced no rows positionId={} symbol={} carriedNoFx={} — FX scraper likely down for this currency",
                    position.getId(), symbol, carriedNoFx);
        }
    }

    /**
     * Already-TRY unit-price seed for a day with no usable FX rate, so the row keeps the line flat
     * across the FX gap instead of being skipped. The snapshot assembler multiplies the returned
     * value by {@code contractSize × lots} and by the caller's {@code fxRate=ONE}, so the
     * value MUST be a TRY unit price (never a native USD/EUR price — that × ONE would persist a ~30x
     * inflated row). Prefers backing the most recent successfully-computed TRY market value out to a
     * unit price ({@code marketValueTry / (contractSize × lots)}); falls back to the position's
     * already-TRY {@code entryPrice} (so the row reads the entry TRY notional) when no prior row
     * exists yet — the early-gap case. Returns null when neither seed is resolvable.
     */
    private BigDecimal carryForwardTryUnitPrice(DerivativePosition position,
                                                PortfolioAssetDailySnapshot priorInBatch) {
        if (priorInBatch != null && priorInBatch.getMarketValueTry() != null
                && priorInBatch.getQuantity() != null && priorInBatch.getQuantity().signum() > 0) {
            BigDecimal contractSize = position.getViopContract().getContractSize() != null
                    ? position.getViopContract().getContractSize() : BigDecimal.ONE;
            BigDecimal divisor = contractSize.multiply(priorInBatch.getQuantity());
            if (divisor.signum() > 0) {
                return priorInBatch.getMarketValueTry().divide(divisor, 8, RoundingMode.HALF_UP);
            }
        }
        return position.getEntryPrice();
    }

    /**
     * Synthetic post-close row marking the holding dropping to zero. The value→0 transition is a
     * realization (proceeds settle to realized PnL / cash), NOT a daily market loss — so its daily PnL is
     * 0. Negating the prior market value here surfaced as a phantom −100% daily K/Z on the close day, and
     * any later "Tümü" aggregate that still summed this stale row (e.g. after the lot was deleted) reported
     * a huge daily loss for an otherwise empty portfolio.
     */
    private PortfolioAssetDailySnapshot buildZeroDerivativeSnapshot(DerivativePosition position,
                                                                     LocalDateTime ts,
                                                                     BigDecimal unitPrice) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(position.getPortfolio().getId())
                .assetType(AssetType.VIOP)
                .assetCode(position.getViopContract().getSymbol())
                .trackedAsset(null)
                .snapshotDate(ts.toLocalDate())
                .createdAt(ts)
                .quantity(BigDecimal.ZERO)
                .unitPriceTry(unitPrice != null ? unitPrice : BigDecimal.ZERO)
                .marketValueTry(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .pnlTry(BigDecimal.ZERO)
                .dailyPnlTry(BigDecimal.ZERO)
                .dailyPnlPercent(BigDecimal.ZERO)
                .build();
    }

    /** True if another lot of the same symbol is still held past this close date (so no zero row should be emitted). */
    private boolean hasPeerHoldingAfter(DerivativePosition position, LocalDate thisClose) {
        Long portfolioId = position.getPortfolio().getId();
        String symbol = position.getViopContract().getSymbol();
        Long excludeId = position.getId();
        return derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .anyMatch(p -> p.getId() != null
                        && !p.getId().equals(excludeId)
                        && p.getViopContract() != null
                        && symbol.equals(p.getViopContract().getSymbol())
                        && p.getEntryDate() != null
                        && !p.getEntryDate().isAfter(thisClose)
                        && (p.getCloseDate() == null || p.getCloseDate().isAfter(thisClose)));
    }

    /** Merges rows that share an exact {@code createdAt} for the symbol into a single summed row, deleting the duplicates. */
    public void consolidateSymbolSnapshots(Long portfolioId, String symbol) {
        if (symbol == null) return;
        List<PortfolioAssetDailySnapshot> all = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, AssetType.VIOP, symbol,
                        LocalDateTime.of(1970, 1, 1, 0, 0),
                        LocalDateTime.now().plusYears(1));
        if (all == null || all.isEmpty()) return;
        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot s : all) {
            grouped.computeIfAbsent(s.getCreatedAt(), k -> new ArrayList<>()).add(s);
        }
        List<PortfolioAssetDailySnapshot> toSave = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> entry : grouped.entrySet()) {
            List<PortfolioAssetDailySnapshot> bucket = entry.getValue();
            if (bucket.size() <= 1) continue;
            PortfolioAssetDailySnapshot keeper = mergeDuplicates(bucket, toDelete);
            toSave.add(keeper);
        }
        if (!toSave.isEmpty()) assetSnapshotRepository.saveAll(toSave);
        if (!toDelete.isEmpty()) {
            assetSnapshotRepository.deleteAllByIdInBatch(toDelete);
            log.info("Consolidated viop snapshots portfolioId={} symbol={} merged={} deleted={}",
                    portfolioId, symbol, toSave.size(), toDelete.size());
        }
    }

    private Map<LocalDate, BigDecimal> loadCandleCloses(String symbol, LocalDate from, LocalDate to) {
        LocalDateTime fromDT = from.atStartOfDay();
        LocalDateTime toDT = to.plusDays(1).atStartOfDay().minusSeconds(1);
        Map<LocalDate, BigDecimal> closeByDate = new HashMap<>();
        for (ViopCandle candle : candleRepository
                .findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(symbol, fromDT, toDT)) {
            if (candle.getCandleDate() != null && candle.getClose() != null) {
                closeByDate.put(candle.getCandleDate().toLocalDate(), candle.getClose());
            }
        }
        return closeByDate;
    }

    private PortfolioAssetDailySnapshot mergeDuplicates(List<PortfolioAssetDailySnapshot> bucket,
                                                        List<Long> toDelete) {
        PortfolioAssetDailySnapshot keeper = bucket.get(0);
        BigDecimal mv = nullToZero(keeper.getMarketValueTry());
        BigDecimal pnl = nullToZero(keeper.getPnlTry());
        BigDecimal qty = nullToZero(keeper.getQuantity());
        BigDecimal totalCost = nullToZero(keeper.getTotalCostTry());
        BigDecimal unitNum = nullToZero(keeper.getUnitPriceTry()).multiply(nullToZero(keeper.getQuantity()));
        BigDecimal sumDaily = BigDecimal.ZERO;
        boolean dailyPresent = keeper.getDailyPnlTry() != null;
        if (dailyPresent) sumDaily = sumDaily.add(keeper.getDailyPnlTry());
        for (int i = 1; i < bucket.size(); i++) {
            PortfolioAssetDailySnapshot dup = bucket.get(i);
            mv = mv.add(nullToZero(dup.getMarketValueTry()));
            pnl = pnl.add(nullToZero(dup.getPnlTry()));
            qty = qty.add(nullToZero(dup.getQuantity()));
            totalCost = totalCost.add(nullToZero(dup.getTotalCostTry()));
            unitNum = unitNum.add(nullToZero(dup.getUnitPriceTry()).multiply(nullToZero(dup.getQuantity())));
            if (dup.getDailyPnlTry() != null) {
                sumDaily = sumDaily.add(dup.getDailyPnlTry());
                dailyPresent = true;
            }
            toDelete.add(dup.getId());
        }
        keeper.setMarketValueTry(mv);
        keeper.setPnlTry(pnl);
        keeper.setQuantity(qty);
        keeper.setTotalCostTry(totalCost);
        keeper.setUnitPriceTry(qty.signum() > 0
                ? unitNum.divide(qty, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : keeper.getUnitPriceTry());
        BigDecimal dailyPercent = null;
        if (dailyPresent && mv.signum() != 0) {
            BigDecimal prior = mv.subtract(sumDaily);
            if (prior.signum() > 0) {
                dailyPercent = sumDaily.multiply(new BigDecimal("100"))
                        .divide(prior, MoneyScale.PRICE, RoundingMode.HALF_UP);
            }
        }
        keeper.setDailyPnlTry(dailyPresent ? sumDaily : null);
        keeper.setDailyPnlPercent(dailyPercent);
        return keeper;
    }

    /** FX rate on {@code target} or the nearest earlier day within the 30-day lookback; null if none. */
    static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = target;
        for (int i = 0; i <= FX_LOOKBACK_DAYS; i++) {
            BigDecimal rate = series.get(cursor);
            if (rate != null) return rate;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    /**
     * The nearest-prior rate, or — when the entry predates all loaded FX history (no prior day exists) — the EARLIEST
     * available rate in the series. Mirrors the frontend {@code rateAt} earliest-point fallback so a pre-history entry is
     * valued at the closest known FX in either direction rather than skipped; never falls back to today's spot. Null only
     * when the series carries no usable rate at all (then the caller flat-carries the already-TRY value).
     */
    static BigDecimal priorOrEarliestRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        BigDecimal prior = closestPriorRate(series, target);
        return (prior != null && prior.signum() > 0) ? prior : earliestRate(series);
    }

    /** Earliest (chronologically first) positive rate in the series, or null when none. */
    static BigDecimal earliestRate(Map<LocalDate, BigDecimal> series) {
        if (series == null || series.isEmpty()) return null;
        return series.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().signum() > 0)
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
