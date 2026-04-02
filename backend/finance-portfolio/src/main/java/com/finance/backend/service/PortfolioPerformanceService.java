package com.finance.backend.service;

import com.finance.backend.dto.response.AssetSeriesPoint;
import com.finance.backend.dto.response.PerformancePoint;
import com.finance.backend.mapper.PortfolioSnapshotMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {

    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioSnapshotMapper snapshotMapper;

    @Transactional(readOnly = true)
    public List<PerformancePoint> getPerformance(Long portfolioId, String range, String assetType) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStartDateTime(end, range);

        if (assetType != null && !assetType.isBlank()) {
            return getAssetTypePerformance(portfolioId, AssetType.valueOf(assetType), start, end);
        }

        List<PortfolioDailySnapshot> snapshots = dailySnapshotRepository
                .findByPortfolioIdAndCreatedAtBetweenOrderByCreatedAtAsc(portfolioId, start, end);

        return snapshotMapper.toPerformancePoints(snapshots);
    }

    @Transactional(readOnly = true)
    public List<AssetSeriesPoint> getAssetSeries(Long portfolioId,
                                                  String assetType, String assetCode, String range) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = resolveStartDateTime(end, range);

        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, AssetType.valueOf(assetType), assetCode, start, end);

        return snapshotMapper.toAssetSeriesPoints(snapshots);
    }

    private List<PerformancePoint> getAssetTypePerformance(Long portfolioId, AssetType assetType,
                                                            LocalDateTime start, LocalDateTime end) {
        List<PortfolioAssetDailySnapshot> snapshots = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, assetType, start, end);

        Map<LocalDateTime, BigDecimal[]> grouped = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot snap : snapshots) {
            grouped.merge(snap.getCreatedAt(),
                    new BigDecimal[]{snap.getMarketValueTry(), snap.getPnlTry(), snap.getTotalCostTry()},
                    (existing, incoming) -> new BigDecimal[]{
                            existing[0].add(incoming[0]),
                            existing[1].add(incoming[1]),
                            existing[2].add(incoming[2])
                    });
        }

        return grouped.entrySet().stream()
                .map(e -> {
                    BigDecimal marketValue = e.getValue()[0];
                    BigDecimal pnl = e.getValue()[1];
                    BigDecimal cost = e.getValue()[2];
                    BigDecimal pnlPercent = cost.compareTo(BigDecimal.ZERO) > 0
                            ? pnl.multiply(BigDecimal.valueOf(100)).divide(cost, 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return new PerformancePoint(e.getKey(), marketValue, pnl, pnlPercent);
                })
                .toList();
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
