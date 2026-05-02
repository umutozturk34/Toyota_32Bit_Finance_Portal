package com.finance.backend.service;

import com.finance.backend.model.*;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.service.AssetPricingPort.AssetKey;
import com.finance.backend.util.PercentChangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class SnapshotCalculationService {

    private static final int DAILY_LOOKBACK_HOURS = 24;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    public PortfolioAssetDailySnapshot buildAssetSnapshot(Long portfolioId, PortfolioPosition pos,
                                                              LocalDateTime batchTimestamp) {
        BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().marketType(), pos.getAssetCode());
        return buildAggregatedAssetSnapshot(portfolioId, pos.getAssetType(), pos.getAssetCode(),
                batchTimestamp, pos.getQuantity(), pos.entryValue(), price);
    }

    public PortfolioAssetDailySnapshot buildAggregatedAssetSnapshot(Long portfolioId,
                                                                      AssetType assetType,
                                                                      String assetCode,
                                                                      LocalDateTime batchTimestamp,
                                                                      BigDecimal totalQuantity,
                                                                      BigDecimal totalCost,
                                                                      BigDecimal unitPriceTry) {
        BigDecimal unitPrice = unitPriceTry != null ? unitPriceTry : BigDecimal.ZERO;
        BigDecimal qty = totalQuantity != null ? totalQuantity : BigDecimal.ZERO;
        BigDecimal cost = (totalCost != null ? totalCost : BigDecimal.ZERO).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal marketValue = unitPrice.multiply(qty).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(cost).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        DailyDelta daily = computeAssetDailyDelta(portfolioId, assetType, assetCode, marketValue, batchTimestamp);

        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(assetType)
                .assetCode(assetCode)
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

    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(pid);

        List<AssetKey> keys = positions.stream().map(PortfolioPosition::toAssetKey).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(keys);
        return buildAggregateSnapshotAt(portfolio, batchTimestamp, positions, prices);
    }

    public PortfolioDailySnapshot buildAggregateSnapshotAt(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                            List<PortfolioPosition> positions,
                                                            Map<AssetKey, BigDecimal> prices) {
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
        DailyDelta daily = computeAggregateDailyDelta(portfolio.getId(), totalMarketValue, batchTimestamp);

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

    private DailyDelta computeAssetDailyDelta(Long portfolioId, AssetType assetType, String assetCode,
                                               BigDecimal currentMarketValue, LocalDateTime batchTimestamp) {
        LocalDateTime cutoff = batchTimestamp.minusHours(DAILY_LOOKBACK_HOURS);
        return assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        portfolioId, assetType, assetCode, cutoff)
                .map(prior -> deltaFrom(currentMarketValue, prior.getMarketValueTry()))
                .orElse(DailyDelta.EMPTY);
    }

    private DailyDelta computeAggregateDailyDelta(Long portfolioId, BigDecimal currentTotalValue,
                                                   LocalDateTime batchTimestamp) {
        LocalDateTime cutoff = batchTimestamp.minusHours(DAILY_LOOKBACK_HOURS);
        return dailySnapshotRepository
                .findFirstByPortfolioIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(portfolioId, cutoff)
                .map(prior -> deltaFrom(currentTotalValue, prior.getTotalValueTry()))
                .orElse(DailyDelta.EMPTY);
    }

    private static DailyDelta deltaFrom(BigDecimal current, BigDecimal prior) {
        BigDecimal amount = current.subtract(prior).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal percent = prior.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(HUNDRED).divide(prior, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(amount, percent);
    }

    private record DailyDelta(BigDecimal amount, BigDecimal percent) {
        static final DailyDelta EMPTY = new DailyDelta(null, null);
    }
}
