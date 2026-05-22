package com.finance.portfolio.service;
import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.model.PortfolioDailySnapshot;

import com.finance.portfolio.model.PortfolioAssetDailySnapshot;

import com.finance.portfolio.model.Portfolio;

import com.finance.portfolio.model.AssetType;

import com.finance.common.model.TrackedAsset;

import com.finance.shared.service.AssetPricingPort;



import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.common.model.MarketType;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioProperties portfolioProperties;
    private final DerivativeSnapshotAssembler derivativeSnapshotAssembler;

    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshot(Long portfolioId,
                                                                      com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                      LocalDateTime batchTimestamp) {
        if (position.getViopContract() == null) return null;
        if (!position.isOpen() && position.getClosePrice() != null) {
            return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp,
                    position.getClosePrice(), BigDecimal.ONE);
        }
        BigDecimal currentPrice = position.getViopContract().getLastPrice();
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, currentPrice);
    }

    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice) {
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice, null);
    }

    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice,
                                                                       BigDecimal fxRateOverride) {
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice, fxRateOverride, null);
    }

    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice,
                                                                       BigDecimal fxRateOverride,
                                                                       PortfolioAssetDailySnapshot priorOverride) {
        return derivativeSnapshotAssembler.buildAt(portfolioId, position, batchTimestamp,
                exitPrice, fxRateOverride, priorOverride);
    }

    public List<PortfolioAssetDailySnapshot> buildAssetSnapshotsForPositions(Long portfolioId,
                                                                              List<PortfolioPosition> positions,
                                                                              LocalDateTime batchTimestamp) {
        if (positions == null || positions.isEmpty()) return List.of();
        Map<AssetKey, List<PortfolioPosition>> grouped = new LinkedHashMap<>();
        for (PortfolioPosition pos : positions) {
            grouped.computeIfAbsent(pos.toAssetKey(), k -> new ArrayList<>()).add(pos);
        }
        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(new ArrayList<>(grouped.keySet()));
        List<PortfolioAssetDailySnapshot> snapshots = new ArrayList<>(grouped.size());
        for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : grouped.entrySet()) {
            PortfolioPosition first = entry.getValue().get(0);
            BigDecimal totalQty = sumLotField(entry.getValue(), PortfolioPosition::getQuantity);
            BigDecimal totalCost = sumLotField(entry.getValue(), PortfolioPosition::entryValue);
            snapshots.add(buildAggregatedAssetSnapshot(portfolioId, first.getAssetType(),
                    first.getAssetCode(), first.getTrackedAsset(), batchTimestamp,
                    totalQty, totalCost, prices.get(entry.getKey())));
        }
        return snapshots;
    }

    private static BigDecimal sumLotField(List<PortfolioPosition> lots,
                                           Function<PortfolioPosition, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition lot : lots) {
            total = total.add(extractor.apply(lot));
        }
        return total;
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
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(pid);
        List<AssetKey> keys = positions.stream().map(PortfolioPosition::toAssetKey).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(keys);
        List<PortfolioAssetDailySnapshot> contributingRows = fetchLatestHeldAssetRows(pid, positions);
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, derivatives, prices, contributingRows);
    }

    public PortfolioDailySnapshot buildAggregateSnapshotAtFromRows(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                                     List<PortfolioPosition> positions,
                                                                     List<DerivativePosition> derivatives,
                                                                     Map<AssetKey, BigDecimal> prices,
                                                                     List<PortfolioAssetDailySnapshot> rowsForBatch) {
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions,
                derivatives != null ? derivatives : List.of(), prices,
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
                                                              List<DerivativePosition> derivatives,
                                                              Map<AssetKey, BigDecimal> prices,
                                                              List<PortfolioAssetDailySnapshot> contributingRows) {
        java.time.LocalDate snapDate = batchTimestamp.toLocalDate();
        Map<AssetKey, BigDecimal> rowMvByKey = indexMarketValuesByKey(contributingRows);
        SnapshotTotals totals = new SnapshotTotals();
        Set<AssetKey> countedFromRows = new HashSet<>();
        accumulateSpotPositions(positions, snapDate, rowMvByKey, prices, totals, countedFromRows);
        accumulateDerivativePositions(derivatives, snapDate, rowMvByKey, totals, countedFromRows);
        return totals.toAggregateSnapshot(portfolio.getId(), snapDate, batchTimestamp, sumAssetDailies(contributingRows));
    }

    private Map<AssetKey, BigDecimal> indexMarketValuesByKey(List<PortfolioAssetDailySnapshot> rows) {
        Map<AssetKey, PortfolioAssetDailySnapshot> latest = new java.util.HashMap<>();
        for (PortfolioAssetDailySnapshot row : rows) {
            if (row.getAssetType() == null || row.getAssetCode() == null || row.getMarketValueTry() == null) continue;
            AssetKey key = new AssetKey(row.getAssetType().marketType(), row.getAssetCode());
            latest.merge(key, row, SnapshotCalculationService::pickLatestSnapshot);
        }
        Map<AssetKey, BigDecimal> result = new java.util.HashMap<>();
        latest.forEach((k, snap) -> result.put(k, snap.getMarketValueTry()));
        return result;
    }

    private void accumulateSpotPositions(List<PortfolioPosition> positions, java.time.LocalDate snapDate,
                                          Map<AssetKey, BigDecimal> rowMvByKey,
                                          Map<AssetKey, BigDecimal> prices,
                                          SnapshotTotals totals,
                                          Set<AssetKey> countedFromRows) {
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryDate() != null && pos.getEntryDate().toLocalDate().isAfter(snapDate)) continue;
            totals.addEntry(pos.entryValue());
            if (pos.isClosed() && !pos.getExitDate().toLocalDate().isAfter(snapDate)) {
                accumulateClosedSpot(pos, totals);
            } else {
                accumulateOpenSpot(pos, rowMvByKey, prices, totals, countedFromRows);
            }
        }
    }

    private void accumulateClosedSpot(PortfolioPosition pos, SnapshotTotals totals) {
        if (pos.getExitPrice() == null) return;
        BigDecimal realized = pos.getExitPrice().subtract(pos.getEntryPrice()).multiply(pos.getQuantity());
        totals.addRealizedClose(realized, pos.getExitPrice().multiply(pos.getQuantity()));
    }

    private void accumulateOpenSpot(PortfolioPosition pos, Map<AssetKey, BigDecimal> rowMvByKey,
                                     Map<AssetKey, BigDecimal> prices,
                                     SnapshotTotals totals, Set<AssetKey> countedFromRows) {
        AssetKey key = pos.toAssetKey();
        BigDecimal rowMv = rowMvByKey.get(key);
        if (rowMv != null) {
            if (countedFromRows.add(key)) totals.addMarket(rowMv);
            return;
        }
        BigDecimal price = prices.get(key);
        BigDecimal unitPrice = price != null ? price : BigDecimal.ZERO;
        totals.addMarket(pos.currentValue(unitPrice));
    }

    private void accumulateDerivativePositions(List<DerivativePosition> derivatives, java.time.LocalDate snapDate,
                                                 Map<AssetKey, BigDecimal> rowMvByKey,
                                                 SnapshotTotals totals,
                                                 Set<AssetKey> countedFromRows) {
        for (DerivativePosition dpos : derivatives) {
            if (dpos.getEntryDate() == null || dpos.getEntryDate().isAfter(snapDate)) continue;
            if (dpos.getViopContract() == null) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            if (entryNotional == null) continue;
            totals.addEntry(entryNotional);
            boolean closedBySnapDate = dpos.getCloseDate() != null && !dpos.getCloseDate().isAfter(snapDate);
            if (closedBySnapDate) {
                BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
                if (realized != null) totals.addRealizedClose(realized, entryNotional.add(realized));
                continue;
            }
            AssetKey key = new AssetKey(MarketType.VIOP, dpos.getViopContract().getSymbol());
            BigDecimal rowMv = rowMvByKey.get(key);
            if (rowMv != null && countedFromRows.add(key)) totals.addMarket(rowMv);
        }
    }

    private static final class SnapshotTotals {
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal cumulativeRealized = BigDecimal.ZERO;
        BigDecimal closedExitValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;

        void addEntry(BigDecimal v) { totalEntryValue = totalEntryValue.add(v); }
        void addMarket(BigDecimal v) { totalMarketValue = totalMarketValue.add(v); }
        void addRealizedClose(BigDecimal realized, BigDecimal exitValue) {
            cumulativeRealized = cumulativeRealized.add(realized);
            closedExitValue = closedExitValue.add(exitValue);
        }

        PortfolioDailySnapshot toAggregateSnapshot(Long portfolioId, java.time.LocalDate snapDate,
                                                    LocalDateTime batchTimestamp, DailyDelta daily) {
            BigDecimal mv = totalMarketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal realized = cumulativeRealized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal closed = closedExitValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal entry = totalEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal totalValue = mv.add(closed).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, entry, MoneyScale.PRICE);
            BigDecimal pnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
            BigDecimal pnlPct = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
            return PortfolioDailySnapshot.builder()
                    .portfolioId(portfolioId)
                    .snapshotDate(snapDate)
                    .createdAt(batchTimestamp)
                    .totalValueTry(totalValue)
                    .cashTry(realized)
                    .totalCostTry(entry)
                    .totalPnlTry(pnl)
                    .pnlPercent(pnlPct)
                    .dailyPnlTry(daily.amount())
                    .dailyPnlPercent(daily.percent())
                    .build();
        }
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

    private static List<PortfolioAssetDailySnapshot> latestRowPerAsset(List<PortfolioAssetDailySnapshot> rows) {
        Map<String, PortfolioAssetDailySnapshot> latest = new java.util.LinkedHashMap<>();
        List<PortfolioAssetDailySnapshot> anonymous = new java.util.ArrayList<>();
        for (PortfolioAssetDailySnapshot snap : rows) {
            if (snap.getAssetCode() == null || snap.getAssetType() == null) {
                anonymous.add(snap);
                continue;
            }
            String key = snap.getAssetType().name() + ":" + snap.getAssetCode();
            latest.merge(key, snap, SnapshotCalculationService::pickLatestSnapshot);
        }
        List<PortfolioAssetDailySnapshot> result = new java.util.ArrayList<>(latest.values());
        result.addAll(anonymous);
        return result;
    }

    private static PortfolioAssetDailySnapshot pickLatestSnapshot(PortfolioAssetDailySnapshot existing,
                                                                    PortfolioAssetDailySnapshot incoming) {
        if (existing.getCreatedAt() == null) return incoming;
        if (incoming.getCreatedAt() == null) return existing;
        return incoming.getCreatedAt().isAfter(existing.getCreatedAt()) ? incoming : existing;
    }

    private DailyDelta sumAssetDailies(List<PortfolioAssetDailySnapshot> rows) {
        if (rows == null || rows.isEmpty()) return DailyDelta.EMPTY;
        List<PortfolioAssetDailySnapshot> deduped = latestRowPerAsset(rows);
        BigDecimal totalDaily = BigDecimal.ZERO;
        BigDecimal totalPrior = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot r : deduped) {
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
