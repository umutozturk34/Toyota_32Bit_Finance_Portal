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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {


    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PortfolioSnapshotMapper snapshotMapper;

    @Transactional(readOnly = true)
    public List<PerformancePoint> getPerformance(Long portfolioId, String range, String assetType) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = CandlePeriod.fromCode(range).toStartDateTime(end);

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        return filterType != null
                ? getAssetTypePerformance(portfolioId, filterType, start, end)
                : getAggregatePerformance(portfolioId, start, end);
    }

    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = CandlePeriod.fromCode(range).toStartDateTime(end);
        AssetType type = EnumParser.parseOrBadRequest(AssetType.class, assetType, "enum.field.assetType");
        List<PortfolioAssetDailySnapshot> snapshots;
        if (type == AssetType.VIOP) {
            snapshots = assetSnapshotRepository
                    .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                            portfolioId, type, assetCode, start, end);
        } else {
            TrackedAssetType trackedType = TrackedAssetType.valueOf(type.name());
            TrackedAsset tracked = trackedAssetRepository
                    .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, trackedType.normalizeCode(assetCode))
                    .orElse(null);
            if (tracked == null) return List.of();
            snapshots = assetSnapshotRepository
                    .findByPortfolioIdAndTrackedAssetIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                            portfolioId, tracked.getId(), start, end);
        }
        return snapshotMapper.toAssetSeriesPoints(snapshots);
    }

    private List<PerformancePoint> getAggregatePerformance(Long portfolioId,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioAggregateRow> aggregates = assetSnapshotRepository
                .findAggregateByPortfolio(portfolioId, start, end);
        List<PortfolioAssetDailySnapshot> assetSnapshots = assetSnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findOpenByPortfolio(portfolioId);

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> assetsByTimestamp = assetSnapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevTypeValues = null;
        LocalDateTime prevTime = start;

        for (PortfolioAggregateRow agg : aggregates) {
            List<PortfolioAssetDailySnapshot> assets = assetsByTimestamp.getOrDefault(agg.createdAt(), List.of());
            Map<String, BigDecimal> currTypeValues = new LinkedHashMap<>();
            List<PerformanceAssetDetail> details = aggregateByType(assets, currTypeValues);
            List<PerformanceEvent> events = buildEvents(positions, derivatives, prevTime, agg.createdAt(),
                    prevTypeValues, currTypeValues, true);
            result.add(new PerformancePoint(agg.createdAt(), agg.totalValueTry(),
                    agg.totalPnlTry(), agg.pnlPercent(), details, events));
            prevTypeValues = currTypeValues;
            prevTime = agg.createdAt();
        }
        return result;
    }

    private List<PerformanceAssetDetail> aggregateByType(List<PortfolioAssetDailySnapshot> assets,
                                                         Map<String, BigDecimal> outCurrTypeValues) {
        Map<String, BigDecimal[]> typeAgg = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot a : assets) {
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

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt,
                        LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevAssetValues = null;
        LocalDateTime prevTime = start;

        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> e : grouped.entrySet()) {
            Map<String, BigDecimal> currAssetValues = new LinkedHashMap<>();
            AssetCodeAgg agg = aggregateByCode(e.getValue(), currAssetValues);
            List<PerformanceEvent> events = buildEvents(positions, List.of(), prevTime, e.getKey(),
                    prevAssetValues, currAssetValues, false);
            result.add(new PerformancePoint(e.getKey(), agg.totalValue, agg.totalPnl, agg.pnlPercent,
                    agg.details, events));
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
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalPnl = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        List<PerformanceAssetDetail> details = new ArrayList<>();
        for (PortfolioAssetDailySnapshot snap : snaps) {
            totalValue = totalValue.add(snap.getMarketValueTry());
            totalPnl = totalPnl.add(snap.getPnlTry());
            totalCost = totalCost.add(snap.getTotalCostTry());
            details.add(new PerformanceAssetDetail(
                    snap.getAssetCode(), snap.getAssetType().name(),
                    snap.getMarketValueTry(), snap.getPnlTry()));
            outCurrAssetValues.put(snap.getAssetCode(), snap.getMarketValueTry());
        }
        details.sort(Comparator.comparing(PerformanceAssetDetail::valueTry).reversed());
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, totalCost, MoneyScale.PRICE);
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        return new AssetCodeAgg(totalValue, totalPnl, pnlPercent, details);
    }

    private List<PerformanceEvent> buildEvents(List<PortfolioPosition> positions,
                                                List<DerivativePosition> derivatives,
                                                LocalDateTime prevTime, LocalDateTime currentTime,
                                                Map<String, BigDecimal> prevValues,
                                                Map<String, BigDecimal> currValues,
                                                boolean keyByType) {
        List<PerformanceEvent> events = new ArrayList<>();

        List<PortfolioPosition> addedInWindow = positions.stream()
                .filter(p -> p.getEntryDate() != null
                        && p.getEntryDate().isAfter(prevTime)
                        && !p.getEntryDate().isAfter(currentTime))
                .toList();

        List<PortfolioPosition> soldInWindow = positions.stream()
                .filter(p -> p.getExitDate() != null
                        && p.getExitDate().isAfter(prevTime)
                        && !p.getExitDate().isAfter(currentTime))
                .toList();

        List<DerivativePosition> derivativesAdded = derivatives == null ? List.of() : derivatives.stream()
                .filter(d -> d.getEntryDate() != null && d.getViopContract() != null)
                .filter(d -> {
                    LocalDateTime entry = d.getEntryDate().atStartOfDay();
                    return entry.isAfter(prevTime) && !entry.isAfter(currentTime);
                })
                .toList();

        List<DerivativePosition> derivativesClosed = derivatives == null ? List.of() : derivatives.stream()
                .filter(d -> d.getCloseDate() != null && d.getViopContract() != null)
                .filter(d -> {
                    LocalDateTime closeTs = d.getCloseDate().atStartOfDay();
                    return closeTs.isAfter(prevTime) && !closeTs.isAfter(currentTime);
                })
                .toList();

        boolean hasTradeEvent = !addedInWindow.isEmpty() || !soldInWindow.isEmpty()
                || !derivativesAdded.isEmpty() || !derivativesClosed.isEmpty();
        if (hasTradeEvent) {
            for (PortfolioPosition pos : addedInWindow) {
                events.add(new PerformanceEvent(
                        PerformanceEventType.POSITION_ADDED,
                        pos.getAssetType().name(),
                        pos.getAssetCode(),
                        pos.entryValue()));
            }
            for (PortfolioPosition pos : soldInWindow) {
                BigDecimal proceeds = pos.getExitPrice() != null
                        ? pos.getExitPrice().multiply(pos.getQuantity())
                        : BigDecimal.ZERO;
                events.add(new PerformanceEvent(
                        PerformanceEventType.POSITION_SOLD,
                        pos.getAssetType().name(),
                        pos.getAssetCode(),
                        proceeds));
            }
            for (DerivativePosition d : derivativesAdded) {
                BigDecimal notional = d.nominalExposure();
                events.add(new PerformanceEvent(
                        PerformanceEventType.POSITION_ADDED,
                        AssetType.VIOP.name(),
                        d.getViopContract().getSymbol(),
                        notional != null ? notional : BigDecimal.ZERO));
            }
            for (DerivativePosition d : derivativesClosed) {
                BigDecimal notional = d.nominalExposure();
                events.add(new PerformanceEvent(
                        PerformanceEventType.POSITION_SOLD,
                        AssetType.VIOP.name(),
                        d.getViopContract().getSymbol(),
                        notional != null ? notional : BigDecimal.ZERO));
            }
        } else if (prevValues != null) {
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(prevValues.keySet());
            allKeys.addAll(currValues.keySet());
            for (String key : allKeys) {
                BigDecimal prev = prevValues.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal curr = currValues.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal diff = curr.subtract(prev);
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    events.add(new PerformanceEvent(
                            diff.compareTo(BigDecimal.ZERO) > 0 ? PerformanceEventType.MARKET_UP : PerformanceEventType.MARKET_DOWN,
                            keyByType ? key : null, keyByType ? null : key, diff.abs()));
                }
            }
        }
        return events;
    }
}
