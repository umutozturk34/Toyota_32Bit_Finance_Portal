package com.finance.portfolio.service;


import com.finance.portfolio.dto.internal.PortfolioAggregateRow;
import com.finance.portfolio.dto.response.AssetSeriesPoint;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.dto.response.PerformancePoint;
import com.finance.portfolio.mapper.PortfolioSnapshotMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.shared.model.CandlePeriod;
import com.finance.portfolio.model.PerformanceEventType;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.shared.util.EnumParser;
import com.finance.shared.util.PercentChangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {


    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PortfolioSnapshotMapper snapshotMapper;
    private final PerformanceEventAssembler eventAssembler;
    private final PerformanceAggregationHelper aggregationHelper;

    @Transactional(readOnly = true)
    public List<PerformancePoint> getPerformance(Long portfolioId, String range, String assetType) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = CandlePeriod.fromCode(range).toStartDateTime(end);

        if ("CASH".equalsIgnoreCase(assetType)) {
            return getCashPerformance(portfolioId, start, end);
        }
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        return filterType != null
                ? getAssetTypePerformance(portfolioId, filterType, start, end)
                : getAggregatePerformance(portfolioId, start, end);
    }

    private List<PerformancePoint> getCashPerformance(Long portfolioId,
                                                       LocalDateTime start, LocalDateTime end) {
        List<PortfolioAggregateRow> aggregates = dailySnapshotRepository
                .findAggregateByPortfolio(portfolioId, start, end);
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);

        List<PerformancePoint> result = new ArrayList<>(aggregates.size());
        LocalDateTime prevTime = start;
        for (PortfolioAggregateRow agg : aggregates) {
            LocalDate snapDate = agg.createdAt().toLocalDate();
            BigDecimal realized = agg.cashTry() != null ? agg.cashTry() : BigDecimal.ZERO;
            BigDecimal closedCost = BigDecimal.ZERO;
            for (PortfolioPosition pos : positions) {
                if (pos.isClosed() && pos.getExitDate() != null
                        && !pos.getExitDate().toLocalDate().isAfter(snapDate)) {
                    closedCost = closedCost.add(pos.entryValue());
                }
            }
            for (DerivativePosition d : derivatives) {
                if (d.getCloseDate() != null && !d.getCloseDate().isAfter(snapDate)) {
                    BigDecimal notional = d.nominalExposure();
                    if (notional != null) closedCost = closedCost.add(notional);
                }
            }
            BigDecimal cumulativePercent = closedCost.signum() > 0
                    ? realized.multiply(new BigDecimal("100"))
                            .divide(closedCost, MoneyScale.PRICE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            List<PerformanceEvent> closeEvents = eventAssembler.realizedCloseEvents(positions, derivatives, prevTime, agg.createdAt());
            result.add(new PerformancePoint(agg.createdAt(), realized, BigDecimal.ZERO,
                    realized, cumulativePercent, List.of(), closeEvents));
            prevTime = agg.createdAt();
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range) {
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
        List<AssetSeriesPoint> base = snapshotMapper.toAssetSeriesPoints(snapshots);
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
                    p.timestamp(), p.unitPriceTry(), p.marketValueTry(), p.pnlTry(),
                    p.dailyPnlTry(), p.dailyPnlPercent(), events));
            prev = p.timestamp();
        }
        return promoteSoldEventsToZeroPoint(result);
    }

    private List<AssetSeriesPoint> promoteSoldEventsToZeroPoint(List<AssetSeriesPoint> series) {
        if (series.size() < 2) return series;
        List<AssetSeriesPoint> result = new ArrayList<>(series);
        for (int i = 0; i < result.size() - 1; i++) {
            AssetSeriesPoint pre = result.get(i);
            AssetSeriesPoint post = result.get(i + 1);
            if (!isSellDropPair(pre, post)) continue;
            List<PerformanceEvent> soldEvents = pre.events().stream()
                    .filter(e -> e.type() == PerformanceEventType.POSITION_SOLD)
                    .toList();
            if (soldEvents.isEmpty()) continue;
            List<PerformanceEvent> merged = new ArrayList<>(post.events());
            merged.addAll(soldEvents);
            result.set(i + 1, post.withEvents(merged));
        }
        return result;
    }

    private static boolean isSellDropPair(AssetSeriesPoint pre, AssetSeriesPoint post) {
        return pre.timestamp().toLocalDate().equals(post.timestamp().toLocalDate())
                && isPositive(pre.marketValueTry())
                && isZero(post.marketValueTry());
    }

    private static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    private static boolean isZero(BigDecimal v) {
        return v != null && v.signum() == 0;
    }

    private List<PerformancePoint> getAggregatePerformance(Long portfolioId,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioAggregateRow> aggregates = dailySnapshotRepository
                .findAggregateByPortfolio(portfolioId, start, end);
        List<PortfolioAssetDailySnapshot> assetSnapshots = assetSnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> assetsByCreatedAt = assetSnapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevTypeValues = null;
        LocalDateTime prevTime = start;

        for (PortfolioAggregateRow agg : aggregates) {
            List<PortfolioAssetDailySnapshot> assets = assetsByCreatedAt.getOrDefault(
                    agg.createdAt(), List.of());
            Map<String, BigDecimal> currTypeValues = new LinkedHashMap<>();
            List<PerformanceAssetDetail> details = aggregationHelper.aggregateByType(assets, currTypeValues);
            List<PerformanceEvent> events = eventAssembler.buildEvents(positions, derivatives, prevTime, agg.createdAt());
            result.add(new PerformancePoint(agg.createdAt(), agg.totalValueTry(), agg.cashTry(),
                    agg.totalPnlTry(), agg.pnlPercent(), details, events));
            prevTypeValues = currTypeValues;
            prevTime = agg.createdAt();
        }
        return result;
    }

    private List<PerformancePoint> getAssetTypePerformance(Long portfolioId, AssetType assetType,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, assetType, start, end);
        List<PortfolioPosition> positions = assetType == AssetType.VIOP
                ? List.of()
                : positionRepository.findByPortfolioId(portfolioId).stream()
                        .filter(p -> p.getAssetType() == assetType)
                        .toList();
        List<DerivativePosition> derivatives = assetType == AssetType.VIOP
                ? derivativePositionRepository.findByPortfolioId(portfolioId)
                : List.of();

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevAssetValues = null;
        LocalDateTime prevTime = start;

        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> e : grouped.entrySet()) {
            Map<String, BigDecimal> currAssetValues = new LinkedHashMap<>();
            PerformanceAggregationHelper.AssetCodeAgg agg = aggregationHelper.aggregateByCode(e.getValue(), currAssetValues);
            List<PerformanceEvent> events = eventAssembler.buildEvents(positions, derivatives, prevTime, e.getKey());
            result.add(new PerformancePoint(e.getKey(), agg.totalValue(), BigDecimal.ZERO, agg.totalPnl(),
                    agg.pnlPercent(), agg.details(), events));
            prevAssetValues = currAssetValues;
            prevTime = e.getKey();
        }
        return result;
    }

}
