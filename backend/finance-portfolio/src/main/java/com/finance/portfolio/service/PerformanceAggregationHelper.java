package com.finance.portfolio.service;

import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.shared.util.PercentChangeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups a single timestamp's per-asset snapshot rows into the breakdown detail shown on each
 * performance point: either by asset type or by asset code. Lot rows of the same asset are summed
 * first, results are sorted by value desc, and the by-code view caps to a top-N with an OTHER bucket.
 */
@Component
@RequiredArgsConstructor
public class PerformanceAggregationHelper {

    private final PortfolioProperties portfolioProperties;

    /** Detail rows grouped by asset type; also populates {@code outCurrTypeValues} with per-type market values for delta tracking. */
    public List<PerformanceAssetDetail> aggregateByType(List<PortfolioAssetDailySnapshot> assets,
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

    /** Totals and capped detail rows grouped by asset code, returning value/PnL/percent for one performance point. */
    public AssetCodeAgg aggregateByCode(List<PortfolioAssetDailySnapshot> snaps,
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

    /** Keeps the top-N details and folds the rest into a single OTHER row; input must be pre-sorted by value desc. */
    public List<PerformanceAssetDetail> capDetailsWithOther(List<PerformanceAssetDetail> sortedDetails) {
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

    private static List<PortfolioAssetDailySnapshot> sumByAssetCodeWithinGroup(List<PortfolioAssetDailySnapshot> snaps) {
        Map<String, PortfolioAssetDailySnapshot> summed = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot snap : snaps) {
            if (snap.getAssetCode() == null || snap.getAssetType() == null) {
                summed.put("anon-" + System.identityHashCode(snap), snap);
                continue;
            }
            String key = snap.getAssetType().name() + ":" + snap.getAssetCode();
            summed.merge(key, snap, PerformanceAggregationHelper::addLotSnapshots);
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

    public record AssetCodeAgg(BigDecimal totalValue, BigDecimal totalPnl, BigDecimal pnlPercent,
                                List<PerformanceAssetDetail> details) {
    }
}
