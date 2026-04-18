package com.finance.backend.service;

import com.finance.backend.dto.response.AssetSeriesPoint;
import com.finance.backend.dto.response.PerformanceAssetDetail;
import com.finance.backend.dto.response.PerformanceEvent;
import com.finance.backend.dto.response.PerformancePoint;
import com.finance.backend.mapper.PortfolioSnapshotMapper;
import com.finance.backend.util.EnumParser;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.model.PortfolioTransaction;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {

    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioTransactionRepository transactionRepository;
    private final PortfolioSnapshotMapper snapshotMapper;

    @Transactional(readOnly = true)
    public List<PerformancePoint> getPerformance(Long portfolioId, String range, String assetType) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStartDateTime(end, range);

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "asset type");
        if (filterType != null) {
            return getAssetTypePerformance(portfolioId, filterType, start, end);
        }
        return getAggregatePerformance(portfolioId, start, end);
    }

    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStartDateTime(end, range);

        AssetType type = EnumParser.parseOrBadRequest(AssetType.class, assetType, "asset type");

        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, type, assetCode, start, end);

        return snapshotMapper.toAssetSeriesPoints(snapshots);
    }

    private List<PerformancePoint> getAggregatePerformance(Long portfolioId,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);

        List<PortfolioAssetDailySnapshot> assetSnapshots = assetSnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);

        List<PortfolioTransaction> transactions = transactionRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> assetsByTimestamp = assetSnapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt, LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevTypeValues = null;
        LocalDateTime prevTime = start;

        for (PortfolioDailySnapshot snap : snapshots) {
            BigDecimal totalValue = snap.getTotalValueTry().subtract(snap.getCashBalanceTry());
            List<PortfolioAssetDailySnapshot> assets = assetsByTimestamp.getOrDefault(snap.getCreatedAt(), List.of());

            Map<String, BigDecimal[]> typeAgg = new LinkedHashMap<>();
            Map<String, BigDecimal> currTypeValues = new LinkedHashMap<>();
            for (PortfolioAssetDailySnapshot a : assets) {
                typeAgg.merge(a.getAssetType().name(),
                        new BigDecimal[]{a.getMarketValueTry(), a.getPnlTry()},
                        (ex, inc) -> new BigDecimal[]{ex[0].add(inc[0]), ex[1].add(inc[1])});
                currTypeValues.merge(a.getAssetType().name(), a.getMarketValueTry(), BigDecimal::add);
            }

            List<PerformanceAssetDetail> details = typeAgg.entrySet().stream()
                    .map(e -> new PerformanceAssetDetail(e.getKey(), e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .sorted(Comparator.comparing(PerformanceAssetDetail::valueTry).reversed())
                    .toList();

            List<PerformanceEvent> events = buildEvents(transactions, prevTime, snap.getCreatedAt(), prevTypeValues, currTypeValues);

            result.add(new PerformancePoint(snap.getCreatedAt(), totalValue, snap.getTotalPnlTry(), snap.getPnlPercent(), details, events));
            prevTypeValues = currTypeValues;
            prevTime = snap.getCreatedAt();
        }

        return result;
    }

    private List<PerformancePoint> getAssetTypePerformance(Long portfolioId, AssetType assetType,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, assetType, start, end);

        List<PortfolioTransaction> transactions = transactionRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end)
                .stream()
                .filter(tx -> tx.getAssetType() == assetType)
                .toList();

        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(PortfolioAssetDailySnapshot::getCreatedAt, LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> result = new ArrayList<>();
        Map<String, BigDecimal> prevAssetValues = null;
        LocalDateTime prevTime = start;

        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> e : grouped.entrySet()) {
            BigDecimal totalValue = BigDecimal.ZERO;
            BigDecimal totalPnl = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            List<PerformanceAssetDetail> details = new ArrayList<>();
            Map<String, BigDecimal> currAssetValues = new LinkedHashMap<>();

            for (PortfolioAssetDailySnapshot snap : e.getValue()) {
                totalValue = totalValue.add(snap.getMarketValueTry());
                totalPnl = totalPnl.add(snap.getPnlTry());
                totalCost = totalCost.add(snap.getTotalCostTry());
                details.add(new PerformanceAssetDetail(
                        snap.getAssetCode(), snap.getAssetType().name(),
                        snap.getMarketValueTry(), snap.getPnlTry()));
                currAssetValues.put(snap.getAssetCode(), snap.getMarketValueTry());
            }

            details.sort(Comparator.comparing(PerformanceAssetDetail::valueTry).reversed());

            BigDecimal pnlPercent = totalCost.compareTo(BigDecimal.ZERO) > 0
                    ? totalPnl.multiply(BigDecimal.valueOf(100)).divide(totalCost, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            List<PerformanceEvent> events = buildEvents(transactions, prevTime, e.getKey(), prevAssetValues, currAssetValues);

            result.add(new PerformancePoint(e.getKey(), totalValue, totalPnl, pnlPercent, details, events));
            prevAssetValues = currAssetValues;
            prevTime = e.getKey();
        }

        return result;
    }

    private List<PerformanceEvent> buildEvents(List<PortfolioTransaction> allTransactions,
                                                LocalDateTime prevTime, LocalDateTime currentTime,
                                                Map<String, BigDecimal> prevValues,
                                                Map<String, BigDecimal> currValues) {
        List<PerformanceEvent> events = new ArrayList<>();

        List<PortfolioTransaction> matched = allTransactions.stream()
                .filter(tx -> tx.getCreatedAt().isAfter(prevTime) && !tx.getCreatedAt().isAfter(currentTime))
                .toList();

        if (!matched.isEmpty()) {
            for (PortfolioTransaction tx : matched) {
                events.add(new PerformanceEvent(
                        tx.getSide().name(),
                        tx.getAssetType().name(),
                        tx.getAssetCode(),
                        tx.getTotalCostTry()));
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
                            diff.compareTo(BigDecimal.ZERO) > 0 ? "MARKET_UP" : "MARKET_DOWN",
                            key, null, diff.abs()));
                }
            }
        }

        return events;
    }

    private LocalDateTime resolveStartDateTime(LocalDateTime end, String range) {
        return switch (range) {
            case "1M" -> end.minusMonths(1);
            case "3M" -> end.minusMonths(3);
            case "6M" -> end.minusMonths(6);
            case "1Y" -> end.minusYears(1);
            case "ALL" -> end.minusYears(10);
            default -> end.minusMonths(1);
        };
    }
}
