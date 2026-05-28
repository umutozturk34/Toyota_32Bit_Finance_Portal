package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Log4j2
@Component
@RequiredArgsConstructor
class DerivativeSnapshotAssembler {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int FX_LOOKBACK_DAYS = 30;

    private final AssetPricingPort pricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final HistoricalPricingPort historicalPricingPort;

    PortfolioAssetDailySnapshot buildAt(Long portfolioId, DerivativePosition position,
                                         LocalDateTime batchTimestamp, BigDecimal exitPrice,
                                         BigDecimal fxRateOverride, PortfolioAssetDailySnapshot priorOverride) {
        if (position.getViopContract() == null) return null;
        DerivativeMetrics m = computeMetrics(position, exitPrice, fxRateOverride, batchTimestamp.toLocalDate());
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
                                              BigDecimal fxRateOverride, LocalDate snapDate) {
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        BigDecimal fxRate = fxRateOverride != null && fxRateOverride.signum() > 0
                ? fxRateOverride
                : contractFxRate(position.getViopContract().resolvePriceCurrency(), snapDate);
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

    private BigDecimal contractFxRate(String currency, LocalDate snapDate) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        String upper = currency.toUpperCase();
        if (snapDate != null) {
            Map<LocalDate, BigDecimal> fxSeries = historicalPricingPort.getPriceSeries(
                    MarketType.FOREX, upper, snapDate.minusDays(FX_LOOKBACK_DAYS), snapDate);
            BigDecimal historicalRate = closestPriorRate(fxSeries, snapDate);
            if (historicalRate != null && historicalRate.signum() > 0) return historicalRate;
        }
        BigDecimal liveRate = pricingPort.getExitPriceTry(MarketType.FOREX, upper);
        return liveRate != null && liveRate.signum() > 0 ? liveRate : BigDecimal.ONE;
    }

    private static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = target;
        for (int i = 0; i <= FX_LOOKBACK_DAYS; i++) {
            BigDecimal rate = series.get(cursor);
            if (rate != null) return rate;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    private record DerivativeMetrics(BigDecimal qty, BigDecimal contractSize, BigDecimal unitPrice,
                                      BigDecimal marketValue, BigDecimal totalCost, BigDecimal pnl) {}

    private record DailyDelta(BigDecimal amount, BigDecimal percent) {
        static final DailyDelta EMPTY = new DailyDelta(null, null);
    }
}
