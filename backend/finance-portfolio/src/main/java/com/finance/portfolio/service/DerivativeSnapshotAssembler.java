package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class DerivativeSnapshotAssembler {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    PortfolioAssetDailySnapshot buildAt(Long portfolioId, DerivativePosition position,
                                         LocalDateTime batchTimestamp, BigDecimal exitPrice,
                                         BigDecimal fxRateOverride, PortfolioAssetDailySnapshot priorOverride) {
        if (position.getViopContract() == null) return null;
        DerivativeMetrics m = computeMetrics(position, exitPrice, fxRateOverride);
        DailyDelta delta = resolveDailyDelta(portfolioId, position, m, batchTimestamp, priorOverride);
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(AssetType.VIOP)
                .assetCode(position.getViopContract().getSymbol())
                .trackedAsset(null)
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .quantity(m.qty())
                .unitPriceTry(m.unitPrice())
                .marketValueTry(m.marketValue())
                .totalCostTry(m.totalCost())
                .pnlTry(m.pnl().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                .dailyPnlTry(delta.amount())
                .dailyPnlPercent(delta.percent())
                .build();
    }

    private DerivativeMetrics computeMetrics(DerivativePosition position, BigDecimal exitPrice,
                                              BigDecimal fxRateOverride) {
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        BigDecimal fxRate = fxRateOverride != null && fxRateOverride.signum() > 0
                ? fxRateOverride
                : contractFxRate(position.getViopContract().getCurrency());
        BigDecimal unitPrice = (exitPrice != null ? exitPrice : BigDecimal.ZERO)
                .multiply(fxRate).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal marketValue = unitPrice.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal entryPriceTry = position.getEntryPrice() != null
                ? position.getEntryPrice().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalCost = entryPriceTry.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal perLot = position.getDirection() != null
                ? position.getDirection().pnlPerLot(entryPriceTry, unitPrice, contractSize)
                : null;
        BigDecimal pnl = perLot != null ? perLot.multiply(qty) : BigDecimal.ZERO;
        return new DerivativeMetrics(qty, contractSize, unitPrice, marketValue, totalCost, pnl);
    }

    private DailyDelta resolveDailyDelta(Long portfolioId, DerivativePosition position,
                                          DerivativeMetrics m, LocalDateTime batchTimestamp,
                                          PortfolioAssetDailySnapshot priorOverride) {
        String code = position.getViopContract().getSymbol();
        PortfolioAssetDailySnapshot prior = priorOverride != null
                ? priorOverride
                : assetSnapshotRepository
                        .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                                portfolioId, AssetType.VIOP, code, batchTimestamp)
                        .orElse(null);
        if (prior == null || prior.getUnitPriceTry() == null) return DailyDelta.EMPTY;
        BigDecimal priorPerLot = position.getDirection() != null
                ? position.getDirection().pnlPerLot(prior.getUnitPriceTry(), m.unitPrice(), m.contractSize())
                : null;
        if (priorPerLot == null) return DailyDelta.EMPTY;
        BigDecimal dailyPnl = priorPerLot.multiply(m.qty()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal priorValue = prior.getMarketValueTry();
        BigDecimal dailyPercent = priorValue != null && priorValue.compareTo(BigDecimal.ZERO) > 0
                ? dailyPnl.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(dailyPnl, dailyPercent);
    }

    private BigDecimal contractFxRate(String currency) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = pricingPort.getExitPriceTry(MarketType.FOREX, currency.toUpperCase());
        return rate != null && rate.signum() > 0 ? rate : BigDecimal.ONE;
    }

    private record DerivativeMetrics(BigDecimal qty, BigDecimal contractSize, BigDecimal unitPrice,
                                      BigDecimal marketValue, BigDecimal totalCost, BigDecimal pnl) {}

    private record DailyDelta(BigDecimal amount, BigDecimal percent) {
        static final DailyDelta EMPTY = new DailyDelta(null, null);
    }
}
