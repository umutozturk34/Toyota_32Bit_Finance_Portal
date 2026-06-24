package com.finance.portfolio.service;

import com.finance.common.model.TrackedAsset;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Builds the PER-ASSET snapshot row level: one {@link PortfolioAssetDailySnapshot} per distinct spot
 * asset key, summing its lots' quantity/cost, valuing them at the live mark (or an explicit/aggregated
 * unit price), and deriving the day-over-day delta against the closest prior row of that tracked asset.
 *
 * <p>This is split out from {@link SnapshotCalculationService} because per-asset row construction and
 * portfolio-aggregate folding are two distinct levels of the snapshot pipeline: the row builder owns lot
 * grouping, close-day move accounting and the held-across-the-boundary daily-delta math, while the
 * aggregate owns market-value indexing and total accumulation. The only state these levels share is the
 * stateless prior-row lookup, which lives here because every caller of it is a per-asset path.
 */
@Log4j2
@Service
@RequiredArgsConstructor
class AssetRowSnapshotBuilder {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioProperties portfolioProperties;

    /** One per-asset snapshot per distinct asset, summing lot quantities/costs and valuing at the live OPEN-position mark (forex SELLING) in TRY — the same side as the cards; getExitPricesTry (BUYING) is only for a realised close. */
    List<PortfolioAssetDailySnapshot> buildAssetSnapshotsForPositions(Long portfolioId,
                                                                      List<PortfolioPosition> positions,
                                                                      LocalDateTime batchTimestamp) {
        if (positions == null || positions.isEmpty()) return List.of();
        LocalDate snapDate = batchTimestamp.toLocalDate();
        Map<AssetKey, List<PortfolioPosition>> grouped = new LinkedHashMap<>();
        for (PortfolioPosition pos : positions) {
            grouped.computeIfAbsent(pos.toAssetKey(), k -> new ArrayList<>()).add(pos);
        }
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(new ArrayList<>(grouped.keySet()));
        List<PortfolioAssetDailySnapshot> snapshots = new ArrayList<>(grouped.size());
        for (Map.Entry<AssetKey, List<PortfolioPosition>> entry : grouped.entrySet()) {
            PortfolioAssetDailySnapshot row = buildSymbolRow(portfolioId, entry.getValue(),
                    snapDate, batchTimestamp, prices.get(entry.getKey()));
            if (row != null) snapshots.add(row);
        }
        return snapshots;
    }

    /**
     * Builds a symbol's per-asset row from its lots. OPEN lots set the row's value (quantity / market / cost)
     * and their across-the-day price move; a lot CLOSED TODAY adds ONLY its close-day move (exit − prior close)
     * to the daily delta — never to market value, because {@code accumulateClosedSpot} (in the aggregate level)
     * already folds its realized exit into the aggregate, so counting it here too would double the value (and
     * halve nothing, but inflate "Tümü"). When every lot is closed today the row is priced at the realized exit
     * so the close-day chart point shows exit equity and {@link #computeAssetDelta} books the move via the
     * held-across-boundary quantity. Both the headline daily card and the per-asset detail series read this one
     * row, so they cannot diverge. Returns {@code null} for a group with neither an open nor a closed-today lot.
     */
    private PortfolioAssetDailySnapshot buildSymbolRow(Long portfolioId, List<PortfolioPosition> lots,
                                                        LocalDate snapDate, LocalDateTime batchTimestamp,
                                                        BigDecimal currentPriceTry) {
        PortfolioPosition first = lots.get(0);
        List<PortfolioPosition> openLots = new ArrayList<>();
        List<PortfolioPosition> closedTodayLots = new ArrayList<>();
        for (PortfolioPosition lot : lots) {
            if (!lot.isClosed()) {
                openLots.add(lot);
            } else if (lot.getExitDate() != null && lot.getExitDate().toLocalDate().equals(snapDate)) {
                closedTodayLots.add(lot);
            }
        }
        Optional<PortfolioAssetDailySnapshot> prior = first.getTrackedAsset() != null
                ? findClosestPriorAssetSnapshot(portfolioId, first.getTrackedAsset().getId(), batchTimestamp)
                : Optional.empty();
        if (openLots.isEmpty()) {
            if (closedTodayLots.isEmpty()) return null;
            BigDecimal qty = sumLotField(closedTodayLots, PortfolioPosition::getQuantity);
            BigDecimal cost = sumLotField(closedTodayLots, PortfolioPosition::entryValue);
            return assembleAssetSnapshot(portfolioId, first.getAssetType(), first.getAssetCode(),
                    first.getTrackedAsset(), batchTimestamp, qty, cost,
                    weightedExitPrice(closedTodayLots, qty), prior, BigDecimal.ZERO);
        }
        BigDecimal totalQty = sumLotField(openLots, PortfolioPosition::getQuantity);
        BigDecimal totalCost = sumLotField(openLots, PortfolioPosition::entryValue);
        return assembleAssetSnapshot(portfolioId, first.getAssetType(), first.getAssetCode(),
                first.getTrackedAsset(), batchTimestamp, totalQty, totalCost, currentPriceTry,
                prior, closeDayMove(closedTodayLots, prior));
    }

    /** Quantity-weighted average exit price (TRY) of lots closed today; zero when nothing was sold. */
    private static BigDecimal weightedExitPrice(List<PortfolioPosition> closedTodayLots, BigDecimal totalQty) {
        if (totalQty == null || totalQty.signum() == 0) return BigDecimal.ZERO;
        BigDecimal exitValue = BigDecimal.ZERO;
        for (PortfolioPosition lot : closedTodayLots) {
            if (lot.getExitPrice() != null) {
                exitValue = exitValue.add(lot.getExitPrice().multiply(lot.getQuantity()));
            }
        }
        return exitValue.divide(totalQty, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    /**
     * Day's realized price move of the portion sold today: Σ quantity × (exit − prior close). Falls back to the
     * lot's own entry price when the asset has no prior snapshot (bought and sold the same day), so the round-trip
     * still books its move rather than vanishing from the daily.
     */
    private static BigDecimal closeDayMove(List<PortfolioPosition> closedTodayLots,
                                           Optional<PortfolioAssetDailySnapshot> prior) {
        if (closedTodayLots.isEmpty()) return BigDecimal.ZERO;
        BigDecimal priorPrice = prior.map(PortfolioAssetDailySnapshot::getUnitPriceTry).orElse(null);
        BigDecimal move = BigDecimal.ZERO;
        for (PortfolioPosition lot : closedTodayLots) {
            if (lot.getExitPrice() == null) continue;
            BigDecimal base = priorPrice != null ? priorPrice : lot.getEntryPrice();
            move = move.add(lot.getExitPrice().subtract(base).multiply(lot.getQuantity()));
        }
        return move.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumLotField(List<PortfolioPosition> lots,
                                          Function<PortfolioPosition, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition lot : lots) {
            total = total.add(extractor.apply(lot));
        }
        return total;
    }

    /** Aggregated per-asset row; resolves the day-over-day delta from the closest prior snapshot of that tracked asset. */
    PortfolioAssetDailySnapshot buildAggregatedAssetSnapshot(Long portfolioId,
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

    /** As {@link #buildAggregatedAssetSnapshot} but with the prior snapshot supplied directly, for batched backfill that chains days. */
    PortfolioAssetDailySnapshot buildAggregatedAssetSnapshotWithPrior(Long portfolioId,
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

    private PortfolioAssetDailySnapshot assembleAssetSnapshot(Long portfolioId, AssetType assetType,
                                                              String assetCode, TrackedAsset trackedAsset,
                                                              LocalDateTime batchTimestamp,
                                                              BigDecimal totalQuantity, BigDecimal totalCost,
                                                              BigDecimal unitPriceTry,
                                                              Optional<PortfolioAssetDailySnapshot> priorOpt) {
        return assembleAssetSnapshot(portfolioId, assetType, assetCode, trackedAsset, batchTimestamp,
                totalQuantity, totalCost, unitPriceTry, priorOpt, BigDecimal.ZERO);
    }

    private PortfolioAssetDailySnapshot assembleAssetSnapshot(Long portfolioId, AssetType assetType,
                                                              String assetCode, TrackedAsset trackedAsset,
                                                              LocalDateTime batchTimestamp,
                                                              BigDecimal totalQuantity, BigDecimal totalCost,
                                                              BigDecimal unitPriceTry,
                                                              Optional<PortfolioAssetDailySnapshot> priorOpt,
                                                              BigDecimal extraDailyTry) {
        BigDecimal unitPrice = unitPriceTry != null ? unitPriceTry : BigDecimal.ZERO;
        BigDecimal qty = totalQuantity != null ? totalQuantity : BigDecimal.ZERO;
        BigDecimal cost = (totalCost != null ? totalCost : BigDecimal.ZERO).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal marketValue = unitPrice.multiply(qty).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnl = marketValue.subtract(cost).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        DailyDelta daily = computeAssetDelta(unitPrice, qty, priorOpt);
        BigDecimal dailyAmount = daily.amount();
        BigDecimal dailyPercent = daily.percent();
        if (extraDailyTry != null && extraDailyTry.signum() != 0) {
            // Fold a sold-today lot's close-day move into the still-held lots' daily, then take the percent
            // against YESTERDAY's full symbol value — computeAssetDelta's held-only denominator would overstate it.
            dailyAmount = (dailyAmount != null ? dailyAmount : BigDecimal.ZERO)
                    .add(extraDailyTry).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            dailyPercent = priorFullValuePercent(dailyAmount, priorOpt);
        }

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
                .dailyPnlTry(dailyAmount)
                .dailyPnlPercent(dailyPercent)
                .build();
    }

    /** Daily % against yesterday's full symbol value (priorQty × priorPrice); null when no prior or a zero base. */
    private static BigDecimal priorFullValuePercent(BigDecimal dailyAmount,
                                                    Optional<PortfolioAssetDailySnapshot> priorOpt) {
        if (priorOpt.isEmpty()) return null;
        PortfolioAssetDailySnapshot prior = priorOpt.get();
        if (prior.getQuantity() == null || prior.getUnitPriceTry() == null) return null;
        BigDecimal priorValue = prior.getQuantity().multiply(prior.getUnitPriceTry());
        return priorValue.signum() > 0
                ? dailyAmount.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
    }

    private DailyDelta computeAssetDelta(BigDecimal currentUnitPrice, BigDecimal currentQty,
                                         Optional<PortfolioAssetDailySnapshot> priorOpt) {
        if (priorOpt.isEmpty()) return DailyDelta.EMPTY;
        PortfolioAssetDailySnapshot prior = priorOpt.get();
        BigDecimal priorQty = prior.getQuantity();
        BigDecimal priorPrice = prior.getUnitPriceTry();
        if (priorQty == null || priorPrice == null) return DailyDelta.EMPTY;
        // CONTRIBUTION-IMMUNE: daily P&L = price move of the quantity HELD ACROSS the day boundary only. A lot ADDED
        // today (currentQty > priorQty) must not book its entry P&L as a daily move; a lot CLOSED/reduced today
        // (currentQty < priorQty, → 0 on the close-day zero row) is a realization, not a daily market loss. So weight
        // the price delta by the common held quantity, not yesterday's full quantity.
        BigDecimal heldQty = priorQty.min(currentQty != null ? currentQty : priorQty);
        if (heldQty.signum() <= 0) return DailyDelta.EMPTY;
        BigDecimal priceDelta = currentUnitPrice.subtract(priorPrice);
        BigDecimal amount = heldQty.multiply(priceDelta).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal priorValue = heldQty.multiply(priorPrice);
        BigDecimal percent = priorValue.compareTo(BigDecimal.ZERO) > 0
                ? amount.multiply(HUNDRED).divide(priorValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyDelta(amount, percent);
    }

    Optional<PortfolioAssetDailySnapshot> findClosestPriorAssetSnapshot(
            Long portfolioId, Long trackedAssetId, LocalDateTime batchTimestamp) {
        LocalDateTime target = batchTimestamp.minusHours(portfolioProperties.getSnapshot().getDailyLookbackHours());
        return assetSnapshotRepository
                .findFirstByPortfolioIdAndTrackedAssetIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        portfolioId, trackedAssetId, target);
    }
}
