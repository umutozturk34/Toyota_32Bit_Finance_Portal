package com.finance.portfolio.service;
import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.model.PortfolioDailySnapshot;

import com.finance.portfolio.model.PortfolioAssetDailySnapshot;

import com.finance.portfolio.model.Portfolio;

import com.finance.portfolio.model.AssetType;

import com.finance.common.model.TrackedAsset;

import com.finance.common.service.AssetPricingPort;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.common.service.AssetPricingPort.AssetKey;
import com.finance.common.util.PercentChangeCalculator;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Log4j2
@Service
@RequiredArgsConstructor
public class SnapshotCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioProperties portfolioProperties;

    public PortfolioAssetDailySnapshot buildAssetSnapshot(Long portfolioId, PortfolioPosition pos,
                                                              LocalDateTime batchTimestamp) {
        BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().marketType(), pos.getAssetCode());
        TrackedAsset tracked = pos.getTrackedAsset();
        Optional<PortfolioAssetDailySnapshot> prior = tracked != null
                ? findClosestPriorAssetSnapshot(portfolioId, tracked.getId(), batchTimestamp)
                : Optional.empty();
        return assembleAssetSnapshot(portfolioId, pos.getAssetType(), pos.getAssetCode(), tracked,
                batchTimestamp, pos.getQuantity(), pos.entryValue(), price, prior);
    }

    public PortfolioAssetDailySnapshot buildAggregatedAssetSnapshot(Long portfolioId,
                                                                      AssetType assetType,
                                                                      String assetCode,
                                                                      TrackedAsset trackedAsset,
                                                                      LocalDateTime batchTimestamp,
                                                                      BigDecimal totalQuantity,
                                                                      BigDecimal totalCost,
                                                                      BigDecimal unitPriceTry) {
        Optional<PortfolioAssetDailySnapshot> prior = trackedAsset != null
                ? findClosestPriorAssetSnapshot(portfolioId, trackedAsset.getId(), batchTimestamp)
                : Optional.empty();
        return assembleAssetSnapshot(portfolioId, assetType, assetCode, trackedAsset, batchTimestamp,
                totalQuantity, totalCost, unitPriceTry, prior);
    }

    public PortfolioAssetDailySnapshot buildAggregatedAssetSnapshotWithPrior(Long portfolioId,
                                                                                AssetType assetType,
                                                                                String assetCode,
                                                                                TrackedAsset trackedAsset,
                                                                                LocalDateTime batchTimestamp,
                                                                                BigDecimal totalQuantity,
                                                                                BigDecimal totalCost,
                                                                                BigDecimal unitPriceTry,
                                                                                PortfolioAssetDailySnapshot prior) {
        return assembleAssetSnapshot(portfolioId, assetType, assetCode, trackedAsset, batchTimestamp,
                totalQuantity, totalCost, unitPriceTry, Optional.ofNullable(prior));
    }

    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(pid);
        List<AssetKey> keys = positions.stream().map(PortfolioPosition::toAssetKey).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(keys);
        Map<AssetKey, PortfolioAssetDailySnapshot> priors = fetchClosestPriorAssetSnapshots(pid, positions, batchTimestamp);
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, prices, priors);
    }

    public PortfolioDailySnapshot buildAggregateSnapshotAt(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                            List<PortfolioPosition> positions,
                                                            Map<AssetKey, BigDecimal> prices) {
        Map<AssetKey, PortfolioAssetDailySnapshot> priors = fetchClosestPriorAssetSnapshots(
                portfolio.getId(), positions, batchTimestamp);
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, prices, priors);
    }

    public PortfolioDailySnapshot buildAggregateSnapshotAtWithPriors(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                                       List<PortfolioPosition> positions,
                                                                       Map<AssetKey, BigDecimal> prices,
                                                                       Map<AssetKey, PortfolioAssetDailySnapshot> priorAssets) {
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, prices,
                priorAssets != null ? priorAssets : Map.of());
    }

    private PortfolioAssetDailySnapshot assembleAssetSnapshot(Long portfolioId, AssetType assetType,
                                                                String assetCode, TrackedAsset trackedAsset,
                                                                LocalDateTime batchTimestamp,
                                                                BigDecimal totalQuantity, BigDecimal totalCost,
                                                                BigDecimal unitPriceTry,
                                                                Optional<PortfolioAssetDailySnapshot> priorOpt) {
        BigDecimal unitPrice = unitPriceTry != null ? unitPriceTry : BigDecimal.ZERO;
        BigDecimal qty = totalQuantity != null ? totalQuantity : BigDecimal.ZERO;
        BigDecimal cost = (totalCost != null ? totalCost : BigDecimal.ZERO).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal marketValue = unitPrice.multiply(qty).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(cost).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        DailyDelta daily = computeAssetDelta(unitPrice, priorOpt);

        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(assetType)
                .assetCode(assetCode)
                .trackedAsset(trackedAsset)
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .quantity(qty)
                .unitPriceTry(unitPrice)
                .marketValueTry(marketValue)
                .totalCostTry(cost)
                .pnlTry(pnl)
                .dailyPnlTry(daily.amount())
                .dailyPnlPercent(daily.percent())
                .build();
    }

    private PortfolioDailySnapshot assembleAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                              List<PortfolioPosition> positions,
                                                              Map<AssetKey, BigDecimal> prices,
                                                              Map<AssetKey, PortfolioAssetDailySnapshot> priorAssets) {
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(pos.toAssetKey());
            BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;
            totalMarketValue = totalMarketValue.add(pos.currentValue(unitPrice));
            totalEntryValue = totalEntryValue.add(pos.entryValue());
        }
        totalMarketValue = totalMarketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        totalEntryValue = totalEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);

        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalMarketValue, totalEntryValue, MoneyScale.PRICE);
        BigDecimal totalPnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        DailyDelta daily = computeAggregateDelta(positions, prices, priorAssets);

        return PortfolioDailySnapshot.builder()
                .portfolioId(portfolio.getId())
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .totalValueTry(totalMarketValue)
                .totalCostTry(totalEntryValue)
                .totalPnlTry(totalPnl)
                .pnlPercent(pnlPercent)
                .dailyPnlTry(daily.amount())
                .dailyPnlPercent(daily.percent())
                .build();
    }

    private DailyDelta computeAssetDelta(BigDecimal currentUnitPrice,
                                          Optional<PortfolioAssetDailySnapshot> priorOpt) {
        if (priorOpt.isEmpty()) return DailyDelta.EMPTY;
        PortfolioAssetDailySnapshot prior = priorOpt.get();
        BigDecimal priorQty = prior.getQuantity();
        BigDecimal priorPrice = prior.getUnitPriceTry();
        if (priorQty == null || priorPrice == null) return DailyDelta.EMPTY;
        BigDecimal priceDelta = currentUnitPrice.subtract(priorPrice);
        BigDecimal amount = priorQty.multiply(priceDelta).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal priorValue = priorQty.multiply(priorPrice);
        BigDecimal percent = priorValue.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(amount, percent);
    }

    private DailyDelta computeAggregateDelta(List<PortfolioPosition> positions,
                                              Map<AssetKey, BigDecimal> currentPrices,
                                              Map<AssetKey, PortfolioAssetDailySnapshot> priorAssets) {
        if (priorAssets == null || priorAssets.isEmpty()) return DailyDelta.EMPTY;
        Map<AssetKey, Boolean> seen = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalPriorValue = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (seen.put(key, true) != null) continue;
            PortfolioAssetDailySnapshot prior = priorAssets.get(key);
            if (prior == null) continue;
            BigDecimal currentPrice = currentPrices.get(key);
            if (currentPrice == null) continue;
            BigDecimal priorQty = prior.getQuantity();
            BigDecimal priorPrice = prior.getUnitPriceTry();
            if (priorQty == null || priorPrice == null) continue;
            totalAmount = totalAmount.add(priorQty.multiply(currentPrice.subtract(priorPrice)));
            totalPriorValue = totalPriorValue.add(priorQty.multiply(priorPrice));
            any = true;
        }
        if (!any) return DailyDelta.EMPTY;
        BigDecimal amount = totalAmount.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal percent = totalPriorValue.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(HUNDRED).divide(totalPriorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(amount, percent);
    }

    private Optional<PortfolioAssetDailySnapshot> findClosestPriorAssetSnapshot(
            Long portfolioId, Long trackedAssetId, LocalDateTime batchTimestamp) {
        LocalDateTime target = batchTimestamp.minusHours(portfolioProperties.getSnapshot().getDailyLookbackHours());
        Optional<PortfolioAssetDailySnapshot> older = assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        portfolioId, trackedAssetId, target);
        Optional<PortfolioAssetDailySnapshot> newer = assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                        portfolioId, trackedAssetId, target);
        return pickClosest(older, newer, target, PortfolioAssetDailySnapshot::getCreatedAt);
    }

    private Map<AssetKey, PortfolioAssetDailySnapshot> fetchClosestPriorAssetSnapshots(
            Long portfolioId, List<PortfolioPosition> positions, LocalDateTime batchTimestamp) {
        Map<AssetKey, PortfolioAssetDailySnapshot> result = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            AssetKey key = pos.toAssetKey();
            if (result.containsKey(key)) continue;
            TrackedAsset tracked = pos.getTrackedAsset();
            if (tracked == null) continue;
            findClosestPriorAssetSnapshot(portfolioId, tracked.getId(), batchTimestamp)
                    .ifPresent(p -> result.put(key, p));
        }
        return result;
    }

    private static <T> Optional<T> pickClosest(Optional<T> older, Optional<T> newer,
                                                 LocalDateTime target,
                                                 Function<T, LocalDateTime> getCreatedAt) {
        if (older.isEmpty()) return newer;
        if (newer.isEmpty()) return older;
        long olderDiff = Math.abs(Duration.between(getCreatedAt.apply(older.get()), target).toSeconds());
        long newerDiff = Math.abs(Duration.between(getCreatedAt.apply(newer.get()), target).toSeconds());
        return olderDiff <= newerDiff ? older : newer;
    }

    private record DailyDelta(BigDecimal amount, BigDecimal percent) {
        static final DailyDelta EMPTY = new DailyDelta(null, null);
    }
}
