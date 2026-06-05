package com.finance.portfolio.service.performance;

import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.mapper.PortfolioSnapshotMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.model.CandlePeriod;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the single-asset value/PnL series (VIOP by symbol, otherwise by tracked asset) over a range,
 * optionally scoped to one VIOP direction, and annotates each point with the trade events in its window.
 * Runs inside the caller's read-only transaction (the public entrypoint on
 * {@link PortfolioPerformanceService} holds it and delegates here).
 */
@Component
@RequiredArgsConstructor
class AssetSeriesBuilder {

    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PortfolioSnapshotMapper snapshotMapper;
    private final ViopDirectionSeriesRecomputer viopDirectionRecomputer;
    private final PerformanceEventAssembler eventAssembler;

    /**
     * Single-asset series, optionally scoped to ONE derivative direction (LONG/SHORT) for VIOP — so a
     * same-symbol hedge can render a separate chart per leg. Snapshots are direction-blind aggregates, so a
     * direction view is RECOMPUTED from the shared contract unit price + only that direction's lots (read-path:
     * no schema change, no rebuild). {@code direction} is ignored for non-VIOP and for the unfiltered (null) view.
     */
    List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                          String assetType, String assetCode, String range, String direction) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = CandlePeriod.fromCode(range).toStartDateTime(end);
        AssetType type = EnumParser.parseOrBadRequest(AssetType.class, assetType, "enum.field.assetType");
        List<PortfolioAssetDailySnapshot> snapshots;
        List<PortfolioPosition> positions;
        List<DerivativePosition> derivatives;
        if (type == AssetType.VIOP) {
            snapshots = assetSnapshotRepository
                    .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                            portfolioId, type, assetCode, start, end);
            positions = List.of();
            derivatives = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                    .filter(d -> d.getViopContract() != null
                            && assetCode.equalsIgnoreCase(d.getViopContract().getSymbol()))
                    .toList();
        } else {
            TrackedAssetType trackedType = TrackedAssetType.valueOf(type.name());
            String normalizedCode = trackedType.normalizeCode(assetCode);
            TrackedAsset tracked = trackedAssetRepository
                    .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalizedCode)
                    .orElse(null);
            if (tracked == null) return List.of();
            snapshots = assetSnapshotRepository
                    .findByPortfolioIdAndTrackedAssetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                            portfolioId, tracked.getId(), start, end);
            positions = positionRepository.findByPortfolioId(portfolioId).stream()
                    .filter(p -> p.getAssetType() == type
                            && p.getAssetCode() != null
                            && normalizedCode.equalsIgnoreCase(p.getAssetCode()))
                    .toList();
            derivatives = List.of();
        }
        // Collapse a symbol's same-timestamp rows so a position's opposite lots (a LONG+SHORT hedge on one
        // symbol) become ONE direction-aware point: each lot persists its own per-lot snapshot, so the raw
        // series otherwise carries TWO points per instant and the chart halves/averages them (the $4492 → $2246
        // artefact). Summing folds the legs — value = cost + signed pnl nets direction.
        List<PortfolioAssetDailySnapshot> collapsed = type == AssetType.VIOP
                ? viopDirectionRecomputer.mergeSameTimestampRows(snapshots) : snapshots;
        DerivativeDirection directionFilter = viopDirectionRecomputer.parseDirectionOrNull(direction);
        if (type == AssetType.VIOP && directionFilter != null) {
            List<DerivativePosition> directionLots = derivatives.stream()
                    .filter(d -> d.getDirection() == directionFilter)
                    .toList();
            collapsed = viopDirectionRecomputer.recomputeDirectionViopSeries(collapsed, directionLots);
            derivatives = directionLots;   // per-point trade events scoped to this direction's lots only
        }
        List<AssetSeriesPoint> base = snapshotMapper.toAssetSeriesPoints(collapsed);
        return attachAssetSeriesEvents(base, positions, derivatives, start);
    }

    private List<AssetSeriesPoint> attachAssetSeriesEvents(List<AssetSeriesPoint> base,
                                                           List<PortfolioPosition> positions,
                                                           List<DerivativePosition> derivatives,
                                                           LocalDateTime windowStart) {
        if (base.isEmpty() || (positions.isEmpty() && derivatives.isEmpty())) return base;
        List<AssetSeriesPoint> result = new ArrayList<>(base.size());
        LocalDateTime prev = windowStart;
        for (AssetSeriesPoint p : base) {
            PerformanceEventAssembler.TradeWindow trades = eventAssembler.detectTrades(positions, derivatives, prev, p.timestamp());
            List<PerformanceEvent> events = trades.hasAny() ? eventAssembler.tradeEvents(trades) : List.of();
            result.add(new AssetSeriesPoint(
                    p.timestamp(), p.unitPriceTry(), p.marketValueTry(), p.totalCostTry(), p.pnlTry(),
                    p.dailyPnlTry(), p.dailyPnlPercent(), events));
            prev = p.timestamp();
        }
        return result;
    }
}
