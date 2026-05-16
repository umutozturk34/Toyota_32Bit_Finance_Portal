package com.finance.portfolio.service;
import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.model.PortfolioDailySnapshot;

import com.finance.portfolio.model.PortfolioAssetDailySnapshot;

import com.finance.portfolio.model.Portfolio;

import com.finance.portfolio.model.AssetType;

import com.finance.common.model.TrackedAsset;

import com.finance.shared.service.AssetPricingPort;



import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.util.PercentChangeCalculator;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Snapshot row for an open VIOP derivative position. Derivatives carry no cash cost basis
     * (margin-based), so totalCost is the entry-side notional (entryPrice × contractSize × lot)
     * and pnl is the realized-or-unrealized P&L. Stored with assetType=VIOP, assetCode=symbol,
     * trackedAsset=null — the new persisted columns (V90) handle this without a TrackedAsset.
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshot(Long portfolioId,
                                                                      com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                      LocalDateTime batchTimestamp) {
        if (position.getViopContract() == null) return null;
        // Closed positions: snapshot freezes at the locked close_price (TRY) so the row keeps
        // contributing the realized P&L to today's aggregate without tracking the contract's
        // live last_price after close. Open: live last_price (native, FX × applied downstream).
        if (!position.isOpen() && position.getClosePrice() != null) {
            return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp,
                    position.getClosePrice(), BigDecimal.ONE);
        }
        BigDecimal currentPrice = position.getViopContract().getLastPrice();
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, currentPrice);
    }

    /**
     * Snapshot row for a VIOP derivative position at a specific point in time using a specific
     * exit price. Used by {@code DerivativePositionService} backfill to persist one row per day
     * between entry and today, reading the close from {@code viop_candles}; daily P&L is derived
     * from the immediately prior persisted snapshot so the aggregate path is self-sufficient
     * (no runtime augmentation).
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice) {
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice, null);
    }

    /**
     * Variant accepting an explicit FX rate (USD/TRY or EUR/TRY) for the snapshot's date —
     * used by {@code DerivativePositionService.backfillSnapshots} to apply per-date historical
     * conversion so portfolio chart timestamps follow the same per-day rule that asset-detail
     * charts use. When {@code fxRateOverride} is null, falls back to the live FX from the
     * pricing strategy.
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice,
                                                                       BigDecimal fxRateOverride) {
        if (position.getViopContract() == null) return null;
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        // exit_price comes from viop_candles in NATIVE currency. Convert to TRY for the snapshot
        // using the per-date FX rate (caller passes via fxRateOverride). entry_price is already
        // TRY-canonical in DB — used as-is.
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

        String code = position.getViopContract().getSymbol();
        Optional<PortfolioAssetDailySnapshot> prior = assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        portfolioId, AssetType.VIOP, code, batchTimestamp);
        BigDecimal dailyPnl = null;
        BigDecimal dailyPercent = null;
        if (prior.isPresent() && prior.get().getUnitPriceTry() != null) {
            BigDecimal priorPrice = prior.get().getUnitPriceTry();
            BigDecimal priorPerLot = position.getDirection() != null
                    ? position.getDirection().pnlPerLot(priorPrice, unitPrice, contractSize)
                    : null;
            if (priorPerLot != null) {
                dailyPnl = priorPerLot.multiply(qty).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
                BigDecimal priorValue = prior.get().getMarketValueTry();
                if (priorValue != null && priorValue.compareTo(BigDecimal.ZERO) > 0) {
                    dailyPercent = dailyPnl.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP);
                }
            }
        }

        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(portfolioId)
                .assetType(AssetType.VIOP)
                .assetCode(code)
                .trackedAsset(null)
                .snapshotDate(batchTimestamp.toLocalDate())
                .createdAt(batchTimestamp)
                .quantity(qty)
                .unitPriceTry(unitPrice)
                .marketValueTry(marketValue)
                .totalCostTry(totalCost)
                .pnlTry(pnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                .dailyPnlTry(dailyPnl)
                .dailyPnlPercent(dailyPercent)
                .build();
    }

    public PortfolioAssetDailySnapshot buildAssetSnapshot(Long portfolioId, PortfolioPosition pos,
                                                              LocalDateTime batchTimestamp) {
        BigDecimal price = pricingPort.getExitPriceTry(pos.getAssetType().marketType(), pos.getAssetCode());
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

    /**
     * Live path. Per-asset rows have already been persisted at {@code batchTimestamp};
     * aggregate's daily P&L is the sum of {@code dailyPnlTry} from the latest per-asset row
     * for each currently-held (assetType, assetCode).
     */
    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(pid);
        List<AssetKey> keys = positions.stream().map(PortfolioPosition::toAssetKey).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(keys);
        List<PortfolioAssetDailySnapshot> contributingRows = fetchLatestHeldAssetRows(pid, positions);
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, prices, contributingRows);
    }

    /**
     * Backfill path. Caller has the per-asset rows for this batch in memory and passes them
     * directly so the aggregate's daily P&L equals their {@code dailyPnlTry} sum.
     */
    public PortfolioDailySnapshot buildAggregateSnapshotAtFromRows(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                                     List<PortfolioPosition> positions,
                                                                     Map<AssetKey, BigDecimal> prices,
                                                                     List<PortfolioAssetDailySnapshot> rowsForBatch) {
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, prices,
                rowsForBatch != null ? rowsForBatch : List.of());
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
                                                              List<PortfolioAssetDailySnapshot> contributingRows) {
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
        DailyDelta daily = sumAssetDailies(contributingRows);

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

    private DailyDelta sumAssetDailies(List<PortfolioAssetDailySnapshot> rows) {
        if (rows == null || rows.isEmpty()) return DailyDelta.EMPTY;
        BigDecimal totalDaily = BigDecimal.ZERO;
        BigDecimal totalPrior = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot r : rows) {
            BigDecimal daily = r.getDailyPnlTry();
            if (daily == null) continue;
            BigDecimal market = r.getMarketValueTry() != null ? r.getMarketValueTry() : BigDecimal.ZERO;
            totalDaily = totalDaily.add(daily);
            totalPrior = totalPrior.add(market.subtract(daily));
            any = true;
        }
        if (!any) return DailyDelta.EMPTY;
        BigDecimal amount = totalDaily.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal percent = totalPrior.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(HUNDRED).divide(totalPrior, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(amount, percent);
    }

    private List<PortfolioAssetDailySnapshot> fetchLatestHeldAssetRows(Long portfolioId,
                                                                        List<PortfolioPosition> positions) {
        Set<String> heldKeys = new HashSet<>();
        for (PortfolioPosition pos : positions) {
            if (pos.getAssetType() != null && pos.getAssetCode() != null) {
                heldKeys.add(pos.getAssetType().name() + "|" + pos.getAssetCode());
            }
        }
        return assetSnapshotRepository.findLatestPerAsset(portfolioId).stream()
                .filter(r -> r.getAssetType() != null && r.getAssetCode() != null
                        && (heldKeys.contains(r.getAssetType().name() + "|" + r.getAssetCode())
                            || r.getAssetType() == AssetType.VIOP))
                .toList();
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

    private BigDecimal contractFxRate(String currency) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = pricingPort.getExitPriceTry(
                com.finance.common.model.MarketType.FOREX, currency.toUpperCase());
        return rate != null && rate.signum() > 0 ? rate : BigDecimal.ONE;
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
