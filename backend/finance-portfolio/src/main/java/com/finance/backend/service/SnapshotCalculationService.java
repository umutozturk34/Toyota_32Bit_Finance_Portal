package com.finance.backend.service;

import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.model.*;
import com.finance.backend.model.value.MoneyTRY;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.UserWalletRepository;
import com.finance.backend.service.AssetPricingPort.AssetKey;
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
    private final UserWalletRepository walletRepository;
    private final PortfolioProperties portfolioProperties;

    public PortfolioAssetDailySnapshot buildAssetSnapshot(Long portfolioId, PortfolioPosition pos,
                                                              LocalDateTime batchTimestamp) {
        BigDecimal price = pricingPort.getPriceTry(pos.getAssetType().marketType(), pos.getAssetCode());
        BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;
        BigDecimal marketValue = unitPrice.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(pos.getTotalCostTry());

        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(pos.getAssetType())
                .assetCode(pos.getAssetCode())
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .quantity(pos.getQuantity())
                .unitPriceTry(unitPrice)
                .marketValueTry(marketValue)
                .totalCostTry(pos.getTotalCostTry())
                .pnlTry(pnl)
                .build();
    }

    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();

        List<PortfolioPosition> allPositions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(pid, BigDecimal.ZERO);

        List<AssetKey> keys = allPositions.stream()
                .map(p -> new AssetKey(p.getAssetType().marketType(), p.getAssetCode()))
                .toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(keys);

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PortfolioPosition pos : allPositions) {
            BigDecimal price = prices.get(new AssetKey(pos.getAssetType().marketType(), pos.getAssetCode()));
            BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;
            BigDecimal marketValue = unitPrice.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP);

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCost = totalCost.add(pos.getTotalCostTry());
        }

        BigDecimal cashBalance = walletRepository.findByPortfolioIdAndCurrency(
                        pid,
                        portfolioProperties.getDefaultCurrency())
                .map(UserWallet::getBalance)
                .map(MoneyTRY::amount)
                .orElse(BigDecimal.ZERO);

        BigDecimal grandTotal = totalMarketValue.add(cashBalance);
        BigDecimal totalPnl = totalMarketValue.subtract(totalCost);
        BigDecimal pnlPercent = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.multiply(new BigDecimal("100")).divide(totalCost, SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return PortfolioDailySnapshot.builder()
                .portfolioId(pid)
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .totalValueTry(grandTotal)
                .totalCostTry(totalCost)
                .totalPnlTry(totalPnl)
                .pnlPercent(pnlPercent)
                .cashBalanceTry(cashBalance)
                .build();
    }
}
