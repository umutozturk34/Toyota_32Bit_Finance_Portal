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
        Map<LocalDate, Map<String, BigDecimal>> out = new LinkedHashMap<>();
        for (PortfolioDailySnapshot s : snapshots) {
            if (s.getSnapshotDate() == null || s.getTotalValueTry() == null) continue;
            List<PortfolioAssetDailySnapshot> dayAssets = assetsByDate.get(s.getSnapshotDate());
            if (dayAssets != null) {
                PerCcyInputs pc = aggregatePerformanceBuilder.perCcyInputs(positions, derivatives, dayAssets, s.getSnapshotDate());
                out.put(s.getSnapshotDate(),
                        frameCalculator.framesForTotal(s.getSnapshotDate(), pc.notionalTry(), pc.fps(), fxByCcy).pnl());
            } else {
                out.put(s.getSnapshotDate(),
                        frameCalculator.framesForTotal(s.getSnapshotDate(), s.getTotalValueTry(), fps, fxByCcy).pnl());
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
