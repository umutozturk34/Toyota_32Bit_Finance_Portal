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
    private final com.finance.portfolio.config.PortfolioProperties portfolioProperties;

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

    /**
     * Cumulative realized P&L over time. Reads from {@code PortfolioDailySnapshot.cashTry} which
     * is persisted as cumulative realized (sum of (exit−entry)·qty for closed spot lots +
     * realizedOrUnrealizedPnl for closed derivatives) — see assembleAggregateSnapshot.
     * Percent = cumulative realized / cumulative closed-position cost basis × 100 (always has a
     * non-zero denominator once anything has closed, unlike the day-over-day delta which is
     * undefined on the first close).
     */
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
            List<PerformanceEvent> closeEvents = realizedCloseEvents(positions, derivatives, prevTime, agg.createdAt());
            result.add(new PerformancePoint(agg.createdAt(), realized, BigDecimal.ZERO,
                    realized, cumulativePercent, List.of(), closeEvents));
            prevTime = agg.createdAt();
        }
        return result;
    }

    private List<PerformanceEvent> realizedCloseEvents(List<PortfolioPosition> positions,
                                                        List<DerivativePosition> derivatives,
                                                        LocalDateTime prevTime, LocalDateTime currentTime) {
        TradeWindow trades = TradeWindow.detect(positions, derivatives, prevTime, currentTime);
        if (trades.spotSold().isEmpty() && trades.derivativesClosed().isEmpty()) return List.of();
        List<PerformanceEvent> events = new ArrayList<>();
        trades.spotSold().forEach(p -> {
            BigDecimal realized = p.realizedPnl();
            events.add(new PerformanceEvent(PerformanceEventType.POSITION_SOLD,
                    p.getAssetType().name(), p.getAssetCode(),
                    p.getQuantity(),
                    realized != null ? realized : BigDecimal.ZERO));
        });
        trades.derivativesClosed().forEach(d -> {
            BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
            events.add(new PerformanceEvent(PerformanceEventType.POSITION_SOLD,
                    AssetType.VIOP.name(), d.getViopContract().getSymbol(),
                    d.getQuantityLot(),
                    realized != null ? realized : BigDecimal.ZERO));
        });
        return events;
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
            TradeWindow trades = TradeWindow.detect(positions, derivatives, prev, p.timestamp());
            List<PerformanceEvent> events = trades.hasAny() ? tradeEvents(trades) : List.of();
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
            List<PerformanceAssetDetail> details = aggregateByType(assets, currTypeValues);
            List<PerformanceEvent> events = buildEvents(positions, derivatives, prevTime, agg.createdAt(),
                    prevTypeValues, currTypeValues, true);
            result.add(new PerformancePoint(agg.createdAt(), agg.totalValueTry(), agg.cashTry(),
                    agg.totalPnlTry(), agg.pnlPercent(), details, events));
            prevTypeValues = currTypeValues;
            prevTime = agg.createdAt();
        }
        return result;
    }

    private List<PerformanceAssetDetail> aggregateByType(List<PortfolioAssetDailySnapshot> assets,
                                                         Map<String, BigDecimal> outCurrTypeValues) {
        List<PortfolioAssetDailySnapshot> deduped = sumByAssetCodeWithinGroup(assets);
        Map<String, BigDecimal[]> typeAgg = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot a : deduped) {
            typeAgg.merge(a.getAssetType().name(),
                    new BigDecimal[]{a.getMarketValueTry(), a.getPnlTry()},
                    (ex, inc) -> new BigDecimal[]{ex[0].add(inc[0]), ex[1].add(inc[1])});
            outCurrTypeValues.merge(a.getAssetType().name(), a.getMarketValueTry(), BigDecimal::add);
        }
        return typeAgg.entrySet().stream()
                .map(e -> new PerformanceAssetDetail(e.getKey(), e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparing(PerformanceAssetDetail::valueTry).reversed())
                .toList();
    }

    /**
     * Folds per-lot snapshot rows sharing the same (assetType, assetCode) inside a single
     * timestamp group into one row with summed quantity, marketValue, cost and pnl. Required
     * because historical data was written one row per position; after the write-side switch to
     * aggregated writes (see {@link SnapshotCalculationService#buildAssetSnapshotsForPositions})
     * each group has at most one row per asset, but the chart must still display correctly for
     * pre-fix snapshots.
     */
    private static List<PortfolioAssetDailySnapshot> sumByAssetCodeWithinGroup(List<PortfolioAssetDailySnapshot> snaps) {
        Map<String, PortfolioAssetDailySnapshot> summed = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot snap : snaps) {
            if (snap.getAssetCode() == null || snap.getAssetType() == null) {
                summed.put("anon-" + System.identityHashCode(snap), snap);
                continue;
            }
            String key = snap.getAssetType().name() + ":" + snap.getAssetCode();
            summed.merge(key, snap, PortfolioPerformanceService::addLotSnapshots);
        }
        return new ArrayList<>(summed.values());
    }

    private static PortfolioAssetDailySnapshot addLotSnapshots(PortfolioAssetDailySnapshot a,
                                                                PortfolioAssetDailySnapshot b) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(a.getPortfolioId())
                .assetType(a.getAssetType())
                .assetCode(a.getAssetCode())
                .trackedAsset(a.getTrackedAsset())
                .snapshotDate(a.getSnapshotDate())
                .createdAt(a.getCreatedAt())
                .quantity(nullSafeAdd(a.getQuantity(), b.getQuantity()))
                .unitPriceTry(a.getUnitPriceTry())
                .marketValueTry(nullSafeAdd(a.getMarketValueTry(), b.getMarketValueTry()))
                .totalCostTry(nullSafeAdd(a.getTotalCostTry(), b.getTotalCostTry()))
                .pnlTry(nullSafeAdd(a.getPnlTry(), b.getPnlTry()))
                .dailyPnlTry(nullSafeAdd(a.getDailyPnlTry(), b.getDailyPnlTry()))
                .dailyPnlPercent(a.getDailyPnlPercent())
                .build();
    }

    private static BigDecimal nullSafeAdd(BigDecimal x, BigDecimal y) {
        if (x == null) return y;
        if (y == null) return x;
        return x.add(y);
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
            AssetCodeAgg agg = aggregateByCode(e.getValue(), currAssetValues);
            List<PerformanceEvent> events = buildEvents(positions, derivatives, prevTime, e.getKey(),
                    prevAssetValues, currAssetValues, false);
            result.add(new PerformancePoint(e.getKey(), agg.totalValue, BigDecimal.ZERO, agg.totalPnl,
                    agg.pnlPercent, agg.details, events));
            prevAssetValues = currAssetValues;
            prevTime = e.getKey();
        }
        return result;
    }

    private record AssetCodeAgg(BigDecimal totalValue, BigDecimal totalPnl, BigDecimal pnlPercent,
                                 List<PerformanceAssetDetail> details) {
    }

    private AssetCodeAgg aggregateByCode(List<PortfolioAssetDailySnapshot> snaps,
                                          Map<String, BigDecimal> outCurrAssetValues) {
        List<PortfolioAssetDailySnapshot> deduped = sumByAssetCodeWithinGroup(snaps);
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<PerformanceAssetDetail> details = new ArrayList<>();
        for (PortfolioAssetDailySnapshot snap : deduped) {
            totalValue = totalValue.add(snap.getMarketValueTry());
            totalPnl = totalPnl.add(snap.getPnlTry());
            totalCost = totalCost.add(snap.getTotalCostTry());
            details.add(new PerformanceAssetDetail(
                    snap.getAssetCode(), snap.getAssetType().name(),
                    snap.getMarketValueTry(), snap.getPnlTry()));
            outCurrAssetValues.put(snap.getAssetCode(), snap.getMarketValueTry());
        }
        details.sort(Comparator.comparing(PerformanceAssetDetail::valueTry).reversed());
        List<PerformanceAssetDetail> capped = capDetailsWithOther(details);
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, totalCost, MoneyScale.PRICE);
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        return new AssetCodeAgg(totalValue, totalPnl, pnlPercent, capped);
    }

    private List<PerformanceAssetDetail> capDetailsWithOther(List<PerformanceAssetDetail> sortedDetails) {
        int topN = portfolioProperties.getPerformance().getDetailTopNLimit();
        if (sortedDetails.size() <= topN) return sortedDetails;
        List<PerformanceAssetDetail> top = sortedDetails.subList(0, topN - 1);
        List<PerformanceAssetDetail> rest = sortedDetails.subList(topN - 1, sortedDetails.size());
        BigDecimal restValue = rest.stream().map(PerformanceAssetDetail::valueTry)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restPnl = rest.stream().map(PerformanceAssetDetail::pnlTry)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PerformanceAssetDetail> result = new ArrayList<>(top.size() + 1);
        result.addAll(top);
        result.add(new PerformanceAssetDetail("OTHER", "OTHER", restValue, restPnl));
        return result;
    }

    private List<PerformanceEvent> buildEvents(List<PortfolioPosition> positions,
                                                List<DerivativePosition> derivatives,
                                                LocalDateTime prevTime, LocalDateTime currentTime,
                                                Map<String, BigDecimal> prevValues,
                                                Map<String, BigDecimal> currValues,
                                                boolean keyByType) {
        TradeWindow trades = TradeWindow.detect(positions, derivatives, prevTime, currentTime);
        if (trades.hasAny()) return tradeEvents(trades);
        return List.of();
    }

    private List<PerformanceEvent> tradeEvents(TradeWindow trades) {
        List<PerformanceEvent> events = new ArrayList<>();
        trades.spotAdded().forEach(p -> events.add(spotEvent(p, PerformanceEventType.POSITION_ADDED, p.entryValue())));
        trades.spotSold().forEach(p -> events.add(spotEvent(p, PerformanceEventType.POSITION_SOLD, spotProceeds(p))));
        trades.derivativesAdded().forEach(d -> events.add(derivativeEvent(
                d, PerformanceEventType.POSITION_ADDED, nullSafe(d.nominalExposure()))));
        trades.derivativesClosed().forEach(d -> events.add(derivativeEvent(
                d, PerformanceEventType.POSITION_SOLD, derivativeProceeds(d))));
        return events;
    }

    private PerformanceEvent spotEvent(PortfolioPosition pos, PerformanceEventType type, BigDecimal value) {
        return new PerformanceEvent(type, pos.getAssetType().name(), pos.getAssetCode(),
                pos.getQuantity(), value);
    }

    private PerformanceEvent derivativeEvent(DerivativePosition d, PerformanceEventType type, BigDecimal value) {
        return new PerformanceEvent(type, AssetType.VIOP.name(), d.getViopContract().getSymbol(),
                d.getQuantityLot(), value);
    }

    private static BigDecimal spotProceeds(PortfolioPosition p) {
        return p.getExitPrice() != null ? p.getExitPrice().multiply(p.getQuantity()) : BigDecimal.ZERO;
    }

    private static BigDecimal derivativeProceeds(DerivativePosition d) {
        BigDecimal entryNotional = d.nominalExposure();
        BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
        if (entryNotional != null && realized != null) return entryNotional.add(realized);
        return entryNotional != null ? entryNotional : BigDecimal.ZERO;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private record TradeWindow(
            List<PortfolioPosition> spotAdded,
            List<PortfolioPosition> spotSold,
            List<DerivativePosition> derivativesAdded,
            List<DerivativePosition> derivativesClosed) {

        static TradeWindow detect(List<PortfolioPosition> positions,
                                  List<DerivativePosition> derivatives,
                                  LocalDateTime prevTime, LocalDateTime currentTime) {
            return new TradeWindow(
                    inWindow(positions, p -> p.getEntryDate(), prevTime, currentTime),
                    inWindow(positions, p -> p.getExitDate(), prevTime, currentTime),
                    inDateWindow(derivatives, d -> d.getEntryDate(), prevTime, currentTime),
                    inDateWindow(derivatives, d -> d.getCloseDate(), prevTime, currentTime));
        }

        boolean hasAny() {
            return !spotAdded.isEmpty() || !spotSold.isEmpty()
                    || !derivativesAdded.isEmpty() || !derivativesClosed.isEmpty();
        }

        private static List<PortfolioPosition> inWindow(List<PortfolioPosition> source,
                                                        java.util.function.Function<PortfolioPosition, LocalDateTime> extractor,
                                                        LocalDateTime prev, LocalDateTime curr) {
            if (source == null) return List.of();
            java.time.LocalDate prevDate = prev.toLocalDate();
            java.time.LocalDate currDate = curr.toLocalDate();
            return source.stream()
                    .filter(p -> {
                        LocalDateTime ts = extractor.apply(p);
                        if (ts == null) return false;
                        java.time.LocalDate eventDate = ts.toLocalDate();
                        return eventDate.isAfter(prevDate) && !eventDate.isAfter(currDate);
                    })
                    .toList();
        }

        private static List<DerivativePosition> inDateWindow(List<DerivativePosition> source,
                                                              java.util.function.Function<DerivativePosition, java.time.LocalDate> extractor,
                                                              LocalDateTime prev, LocalDateTime curr) {
            if (source == null) return List.of();
            java.time.LocalDate prevDate = prev.toLocalDate();
            java.time.LocalDate currDate = curr.toLocalDate();
            return source.stream()
                    .filter(d -> d.getViopContract() != null)
                    .filter(d -> {
                        java.time.LocalDate date = extractor.apply(d);
                        return date != null && date.isAfter(prevDate) && !date.isAfter(currDate);
                    })
                    .toList();
        }
    }
}
