package com.finance.backend.service;

import com.finance.backend.model.*;
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

    private static final int SCALE = 4;

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;

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
        BigDecimal cost = (totalCost != null ? totalCost : BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal marketValue = unitPrice.multiply(qty).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(cost).setScale(SCALE, RoundingMode.HALF_UP);

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
                .build();
    }

    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(pid);

        List<AssetKey> keys = positions.stream()
                .map(p -> new AssetKey(p.getAssetType().marketType(), p.getAssetCode()))
                .toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(keys);
        return buildAggregateSnapshotAt(portfolio, batchTimestamp, positions, prices);
    }

    public PortfolioDailySnapshot buildAggregateSnapshotAt(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                            List<PortfolioPosition> positions,
                                                            Map<AssetKey, BigDecimal> prices) {
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(new AssetKey(pos.getAssetType().marketType(), pos.getAssetCode()));
            BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;
            totalMarketValue = totalMarketValue.add(pos.currentValue(unitPrice));
            totalEntryValue = totalEntryValue.add(pos.entryValue());
        }
        totalMarketValue = totalMarketValue.setScale(SCALE, RoundingMode.HALF_UP);
        totalEntryValue = totalEntryValue.setScale(SCALE, RoundingMode.HALF_UP);

        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalMarketValue, totalEntryValue, SCALE);
        BigDecimal totalPnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;

        return PortfolioDailySnapshot.builder()
                .portfolioId(portfolio.getId())
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .totalValueTry(totalMarketValue)
                .totalCostTry(totalEntryValue)
                .totalPnlTry(totalPnl)
                .pnlPercent(pnlPercent)
                .build();
    }
}
