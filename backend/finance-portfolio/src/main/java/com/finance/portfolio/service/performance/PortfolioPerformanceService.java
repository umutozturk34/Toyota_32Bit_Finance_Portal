package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.RealReturnCalculator;


import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.dto.response.PerformancePoint;
import com.finance.portfolio.model.AssetType;
import com.finance.shared.model.CandlePeriod;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Read-side entry point for the performance/history charts: it resolves the date range, dispatches to the
 * CASH, per-asset-type, aggregate, or single-asset series builders, and exposes the per-currency daily P&L
 * the summary card reuses. The heavy per-point math lives in the dedicated builders/calculators it delegates to.
 */
@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {


    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PerCurrencyFrameCalculator frameCalculator;
    private final PerformanceEntryFootprintBuilder footprintBuilder;
    private final CashRealisedSeriesBuilder cashSeriesBuilder;
    private final AssetSeriesBuilder assetSeriesBuilder;
    private final AggregatePerformanceBuilder aggregatePerformanceBuilder;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<String> FRAME_CCYS = List.of("USD", "EUR");

    /**
     * Per-snapshot portfolio P&L expressed per display currency (USD/EUR), keyed by snapshot date — the SAME
     * per-currency total the performance points carry (value@point-date FX − cost@entry-date FX, closed lots
     * locked at exit-date FX). The compare chart's money overlay reads this so the foreign-currency figure is
     * the true cost-based P&L, not a single-rate conversion of the netted TRY P&L (which mis-converts the cost
     * leg at the point date instead of each lot's entry date). TRY is omitted — callers use totalPnlTry there.
     */
    public Map<LocalDate, Map<String, BigDecimal>> dailyPnlByCcy(Long portfolioId,
                                                                 List<PortfolioDailySnapshot> snapshots) {
        return dailyPnlByCcy(portfolioId, snapshots, null);
    }

    /** As above, optionally scoped to ONE asset type (null = whole portfolio) so a type-filtered summary's
     *  per-currency daily comes from the SAME per-date frame — used by the summary card to express the daily
     *  K/Z in USD/EUR natively (a USD-quoted VİOP reads its own-currency day-move, not the TRY day ÷ one rate). */
    public Map<LocalDate, Map<String, BigDecimal>> dailyPnlByCcy(Long portfolioId,
                                                                 List<PortfolioDailySnapshot> snapshots,
                                                                 AssetType filterType) {
        Map<LocalDate, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        framesByCcy(portfolioId, snapshots, filterType).forEach((date, f) -> out.put(date, f.pnl()));
        return out;
    }

    /**
     * TRY time-weighted-return index (100-based) chained from each snapshot's cash-flow-neutral daily return
     * ({@link PortfolioDailySnapshot#getDailyPnlPercent()}, computed per-asset over the prior-day base so a
     * same-day buy never fakes a move). The first snapshot anchors at 100; each later day multiplies by
     * {@code (1 + dailyReturn)}. TWR neutralises how much capital was at risk when, so the compare line answers
     * "did my holdings beat the benchmark per unit time" independent of contribution size/timing — the
     * money-weighted figure the portfolio card shows is deliberately different (it credits/penalises timing).
     */
    public Map<LocalDate, BigDecimal> twrIndexSeries(List<PortfolioDailySnapshot> snapshots) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        if (snapshots == null || snapshots.isEmpty()) return out;
        BigDecimal index = HUNDRED;
        boolean first = true;
        for (PortfolioDailySnapshot s : snapshots) {
            if (s.getSnapshotDate() == null) continue;
            if (!first) {
                BigDecimal dailyPct = s.getDailyPnlPercent() != null ? s.getDailyPnlPercent() : BigDecimal.ZERO;
                index = index.multiply(BigDecimal.ONE.add(dailyPct.divide(HUNDRED, 10, RoundingMode.HALF_UP)));
            }
            first = false;
            out.put(s.getSnapshotDate(), index);
        }
        return out;
    }

    /**
     * Per-currency (USD/EUR) time-weighted-return index per snapshot date: the TRY {@link #twrIndexSeries}
     * re-expressed in each frame by the per-date FX change. Chaining a day's foreign return telescopes the daily
     * FX into a single ratio, so {@code twr_ccy(t) = twr_try(t) × FX(start)/FX(date)} (FX = TRY per unit of the
     * currency). A book flat in TRY still shows its real USD/EUR move from FX — the compare chart's foreign-frame
     * portfolio line reads this so its slope is FX-aware, not a single-rate conversion of the TRY index. TRY is
     * omitted (the TRY index already carries it).
     */
    public Map<LocalDate, Map<String, BigDecimal>> dailyReturnIndexByCcy(Long portfolioId,
                                                                         List<PortfolioDailySnapshot> snapshots) {
        Map<LocalDate, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> twr = twrIndexSeries(snapshots);
        if (twr.isEmpty()) return out;
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);
        List<RealReturnCalculator.EntryFootprint> fps = footprintBuilder.footprints(positions, derivatives);
        LocalDate start = twr.keySet().stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate end = twr.keySet().stream().max(LocalDate::compareTo).orElseThrow();
        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy = frameCalculator.fxSeriesByCcy(fps, end);
        Map<String, BigDecimal> fxStart = new LinkedHashMap<>();
        for (String ccy : FRAME_CCYS) fxStart.put(ccy, floorFx(fxByCcy.get(ccy), start));
        // A genuinely CLOSED day (no spot lot / derivative still open — all value sits in realized cash) must
        // keep the foreign-frame line flat, matching the flat TRY index and the exit-FX-locked pnlByCcy overlay.
        // So the FX date is carried forward across each closed run: an OPEN day marks at its own FX and a CLOSED
        // day reuses the last open day's, freezing each closed segment (interior or trailing) instead of drifting
        // with post-close FX. The trigger is real open-exposure, NOT a flat TWR — an open book's flat-price day
        // (weekend/holiday) still marks at that day's own FX so genuine FX moves show, not frozen as if closed.
        LocalDate fxDate = null;
        for (Map.Entry<LocalDate, BigDecimal> e : twr.entrySet()) {
            LocalDate date = e.getKey();
            BigDecimal idxTry = e.getValue();
            if (fxDate == null || hasOpenExposureOn(date, positions, derivatives)) fxDate = date;
            Map<String, BigDecimal> index = new LinkedHashMap<>();
            for (String ccy : FRAME_CCYS) {
                BigDecimal fxNow = floorFx(fxByCcy.get(ccy), fxDate);
                BigDecimal fxBase = fxStart.get(ccy);
                index.put(ccy, (fxNow != null && fxNow.signum() > 0 && fxBase != null && fxBase.signum() > 0)
                        ? idxTry.multiply(fxBase).divide(fxNow, 4, RoundingMode.HALF_UP)
                        : idxTry.setScale(4, RoundingMode.HALF_UP));
            }
            out.put(date, index);
        }
        return out;
    }

    private static BigDecimal floorFx(TreeMap<LocalDate, BigDecimal> series, LocalDate date) {
        if (series == null) return null;
        Map.Entry<LocalDate, BigDecimal> e = series.floorEntry(date);
        return e != null ? e.getValue() : null;
    }

    /**
     * True if any spot lot or derivative still carries OPEN market exposure on {@code date}: entered on/before it
     * and not yet exited as of it (exit/close strictly after, matching the snapshot writer's treat-as-closed-on-
     * exit-day rule). {@link #dailyReturnIndexByCcy} uses this to tell a genuinely closed (all-cash) day from an
     * open book's flat-price day (weekend/holiday) — only the former freezes its FX.
     */
    private static boolean hasOpenExposureOn(LocalDate date, List<PortfolioPosition> positions,
                                             List<DerivativePosition> derivatives) {
        for (PortfolioPosition p : positions) {
            if (p.getEntryDate() == null || p.getEntryDate().toLocalDate().isAfter(date)) continue;
            if (p.getExitDate() == null || p.getExitDate().toLocalDate().isAfter(date)) return true;
        }
        for (DerivativePosition d : derivatives) {
            if (d.getEntryDate() == null || d.getEntryDate().isAfter(date)) continue;
            if (d.getCloseDate() == null || d.getCloseDate().isAfter(date)) return true;
        }
        return false;
    }

    private Map<LocalDate, FrameMapsR> framesByCcy(Long portfolioId,
                                                   List<PortfolioDailySnapshot> snapshots,
                                                   AssetType filterType) {
        if (snapshots == null || snapshots.isEmpty()) return Map.of();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(p -> filterType == null || p.getAssetType() == filterType)
                .toList();
        List<DerivativePosition> derivatives = (filterType == null || filterType == AssetType.VIOP)
                ? derivativePositionRepository.findByPortfolioId(portfolioId)
                : List.of();
        List<RealReturnCalculator.EntryFootprint> fps = footprintBuilder.footprints(positions, derivatives);
        LocalDate start = snapshots.stream().map(PortfolioDailySnapshot::getSnapshotDate)
                .filter(Objects::nonNull).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = snapshots.stream().map(PortfolioDailySnapshot::getSnapshotDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(LocalDate.now());
        // Per-date NOTIONAL + direction-aware footprints so the foreign K/Z flips a SHORT's sign without
        // double-counting (mirrors the aggregate/summary value leg). Falls back to the equity total when no
        // per-asset snapshot exists for a date (spot-only / pre-derivative history).
        Map<LocalDate, List<PortfolioAssetDailySnapshot>> assetsByDate = assetSnapshotRepository
                .findByPortfolioIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(portfolioId, start, end).stream()
                .filter(a -> filterType == null || a.getAssetType() == filterType)
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getSnapshotDate,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy = frameCalculator.fxSeriesByCcy(fps, end);
        Map<LocalDate, FrameMapsR> out = new LinkedHashMap<>();
        for (PortfolioDailySnapshot s : snapshots) {
            if (s.getSnapshotDate() == null || s.getTotalValueTry() == null) continue;
            List<PortfolioAssetDailySnapshot> dayAssets = assetsByDate.get(s.getSnapshotDate());
            if (dayAssets != null) {
                PerCcyInputs pc = aggregatePerformanceBuilder.perCcyInputs(positions, derivatives, dayAssets, s.getSnapshotDate());
                out.put(s.getSnapshotDate(),
                        frameCalculator.framesForTotal(s.getSnapshotDate(), pc.notionalTry(), pc.fps(), fxByCcy));
            } else {
                out.put(s.getSnapshotDate(),
                        frameCalculator.framesForTotal(s.getSnapshotDate(), s.getTotalValueTry(), fps, fxByCcy));
            }
        }
        return out;
    }

    /** Performance series for the range: CASH, a specific asset type, or (null type) the portfolio aggregate. */
    @Transactional(readOnly = true)
    public List<PerformancePoint> getPerformance(Long portfolioId, String range, String assetType) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = CandlePeriod.fromCode(range).toStartDateTime(end);

        if ("CASH".equalsIgnoreCase(assetType)) {
            return cashSeriesBuilder.getCashPerformance(portfolioId, start, end);
        }
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        return filterType != null
                ? aggregatePerformanceBuilder.getAssetTypePerformance(portfolioId, filterType, start, end)
                : aggregatePerformanceBuilder.getAggregatePerformance(portfolioId, start, end);
    }

    /** Single-asset value/PnL series over the range (VIOP by symbol, otherwise by tracked asset), with per-point trade events. */
    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range) {
        return getAssetSeries(portfolioId, assetType, assetCode, range, null);
    }

    /**
     * Single-asset series, optionally scoped to ONE derivative direction (LONG/SHORT) for VIOP — so a
     * same-symbol hedge can render a separate chart per leg. Snapshots are direction-blind aggregates, so a
     * direction view is RECOMPUTED from the shared contract unit price + only that direction's lots (read-path:
     * no schema change, no rebuild). {@code direction} is ignored for non-VIOP and for the unfiltered (null) view.
     */
    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range, String direction) {
        return assetSeriesBuilder.getAssetSeries(portfolioId, assetType, assetCode, range, direction);
    }

}
