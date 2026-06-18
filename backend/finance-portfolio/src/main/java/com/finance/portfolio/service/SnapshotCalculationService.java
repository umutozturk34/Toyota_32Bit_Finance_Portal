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
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder of snapshot entities (no persistence) in TRY at the PORTFOLIO-AGGREGATE level: it folds
 * per-asset rows, live prices and derivative positions into one {@link PortfolioDailySnapshot}.
 * Aggregation prefers each asset's latest contributing row market value, sums realized PnL/exit value
 * for closed positions, and derives the day-over-day delta from the per-asset rows. Per-asset row
 * construction is delegated to {@link AssetRowSnapshotBuilder} and derivatives to
 * {@link DerivativeSnapshotCalculator}; the per-asset public methods remain on this class as thin
 * delegators so existing callers stay unchanged.
 */
@Log4j2
@Service
public class SnapshotCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativeSnapshotCalculator derivativeCalculator;
    private final AssetRowSnapshotBuilder assetRowBuilder;

    @Autowired
    public SnapshotCalculationService(AssetPricingPort pricingPort,
                                       PortfolioPositionRepository positionRepository,
                                       DerivativePositionRepository derivativePositionRepository,
                                       PortfolioDailySnapshotRepository dailySnapshotRepository,
                                       PortfolioAssetDailySnapshotRepository assetSnapshotRepository,
                                       PortfolioProperties portfolioProperties,
                                       DerivativeSnapshotAssembler derivativeSnapshotAssembler) {
        this(pricingPort, positionRepository, derivativePositionRepository, dailySnapshotRepository,
                assetSnapshotRepository, portfolioProperties,
                new DerivativeSnapshotCalculator(derivativeSnapshotAssembler));
    }

    SnapshotCalculationService(AssetPricingPort pricingPort,
                                PortfolioPositionRepository positionRepository,
                                DerivativePositionRepository derivativePositionRepository,
                                PortfolioDailySnapshotRepository dailySnapshotRepository,
                                PortfolioAssetDailySnapshotRepository assetSnapshotRepository,
                                PortfolioProperties portfolioProperties,
                                DerivativeSnapshotCalculator derivativeCalculator) {
        this.pricingPort = pricingPort;
        this.positionRepository = positionRepository;
        this.derivativePositionRepository = derivativePositionRepository;
        this.assetSnapshotRepository = assetSnapshotRepository;
        this.derivativeCalculator = derivativeCalculator;
        this.assetRowBuilder = new AssetRowSnapshotBuilder(pricingPort, assetSnapshotRepository, portfolioProperties);
    }

    /** Builds the per-asset snapshot row for a derivative (VIOP) position at the live mark, delegating to the derivative calculator. */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshot(Long portfolioId,
                                                                      com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                      LocalDateTime batchTimestamp) {
        return derivativeCalculator.buildDerivativeAssetSnapshot(portfolioId, position, batchTimestamp);
    }

    /** Value-less close-day VIOP row (only dailyPnlTry) for a lot closed TODAY — feeds the daily K/Z card without
     *  entering the value path; see {@link DerivativeSnapshotCalculator#buildClosedViopDailyRow}. */
    public PortfolioAssetDailySnapshot buildClosedViopDailyRow(Long portfolioId,
            com.finance.portfolio.derivative.model.DerivativePosition position, LocalDateTime batchTimestamp) {
        return derivativeCalculator.buildClosedViopDailyRow(portfolioId, position, batchTimestamp);
    }

    /**
     * Builds a derivative snapshot row valued at an explicit price rather than the live mark, used to
     * book a historical or close-day point.
     *
     * @param exitPrice the price at which the position is valued for this row
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice) {
        return derivativeCalculator.buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice);
    }

    /**
     * As {@link #buildDerivativeAssetSnapshotAt(Long, DerivativePosition, LocalDateTime, BigDecimal)} but with an
     * explicit TRY conversion rate, so a USD-settled contract can be valued against a specific historical FX rate
     * instead of the live one.
     *
     * @param fxRateOverride the FX rate to use for TRY conversion for this row
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice,
                                                                       BigDecimal fxRateOverride) {
        return derivativeCalculator.buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp,
                exitPrice, fxRateOverride);
    }

    /**
     * As the FX-override variant but with the prior snapshot supplied directly instead of being looked up, so
     * batched backfill can chain consecutive days and compute the day-over-day delta against the row it just built.
     *
     * @param priorOverride the previous day's snapshot to diff against, bypassing the repository lookup
     */
    public PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                       com.finance.portfolio.derivative.model.DerivativePosition position,
                                                                       LocalDateTime batchTimestamp,
                                                                       BigDecimal exitPrice,
                                                                       BigDecimal fxRateOverride,
                                                                       PortfolioAssetDailySnapshot priorOverride) {
        return derivativeCalculator.buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp,
                exitPrice, fxRateOverride, priorOverride);
    }

    /** One per-asset snapshot per distinct asset, valued at the live OPEN-position mark; delegates to {@link AssetRowSnapshotBuilder}. */
    public List<PortfolioAssetDailySnapshot> buildAssetSnapshotsForPositions(Long portfolioId,
                                                                              List<PortfolioPosition> positions,
                                                                              LocalDateTime batchTimestamp) {
        return assetRowBuilder.buildAssetSnapshotsForPositions(portfolioId, positions, batchTimestamp);
    }

    /** Aggregated per-asset row; resolves the day-over-day delta from the closest prior snapshot of that tracked asset. */
    public PortfolioAssetDailySnapshot buildAggregatedAssetSnapshot(Long portfolioId,
                                                                      AssetType assetType,
                                                                      String assetCode,
                                                                      TrackedAsset trackedAsset,
                                                                      LocalDateTime batchTimestamp,
                                                                      BigDecimal totalQuantity,
                                                                      BigDecimal totalCost,
                                                                      BigDecimal unitPriceTry) {
        return assetRowBuilder.buildAggregatedAssetSnapshot(portfolioId, assetType, assetCode, trackedAsset,
                batchTimestamp, totalQuantity, totalCost, unitPriceTry);
    }

    /** As {@link #buildAggregatedAssetSnapshot} but with the prior snapshot supplied directly, for batched backfill that chains days. */
    public PortfolioAssetDailySnapshot buildAggregatedAssetSnapshotWithPrior(Long portfolioId,
                                                                                AssetType assetType,
                                                                                String assetCode,
                                                                                TrackedAsset trackedAsset,
                                                                                LocalDateTime batchTimestamp,
                                                                                BigDecimal totalQuantity,
                                                                                BigDecimal totalCost,
                                                                                BigDecimal unitPriceTry,
                                                                                PortfolioAssetDailySnapshot prior) {
        return assetRowBuilder.buildAggregatedAssetSnapshotWithPrior(portfolioId, assetType, assetCode, trackedAsset,
                batchTimestamp, totalQuantity, totalCost, unitPriceTry, prior);
    }

    /** Portfolio-level aggregate "as of now": loads positions, prices and latest held rows, then folds them into one snapshot. */
    public PortfolioDailySnapshot buildAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(pid);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(pid);
        List<AssetKey> keys = positions.stream().map(PortfolioPosition::toAssetKey).toList();
        // OPEN-position mark-to-market (forex SELLING), matching the cards; getExitPricesTry (BUYING) is close-only.
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(keys);
        List<PortfolioAssetDailySnapshot> contributingRows = fetchLatestHeldAssetRows(pid, positions);
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions, derivatives, prices, contributingRows);
    }

    /** Aggregate for a specific day built from already-computed per-asset rows (and prices), used by backfill where rows are known. */
    public PortfolioDailySnapshot buildAggregateSnapshotAtFromRows(Portfolio portfolio, LocalDateTime batchTimestamp,
                                                                     List<PortfolioPosition> positions,
                                                                     List<DerivativePosition> derivatives,
                                                                     Map<AssetKey, BigDecimal> prices,
                                                                     List<PortfolioAssetDailySnapshot> rowsForBatch) {
        return assembleAggregateSnapshot(portfolio, batchTimestamp, positions,
                derivatives != null ? derivatives : List.of(), prices,
                rowsForBatch != null ? rowsForBatch : List.of());
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
        derivativeCalculator.accumulateDerivativePositions(derivatives, snapDate, rowMvByKey, totals, countedFromRows);
        return totals.toAggregateSnapshot(portfolio.getId(), snapDate, batchTimestamp, sumAssetDailies(contributingRows));
    }

    private Map<AssetKey, BigDecimal> indexMarketValuesByKey(List<PortfolioAssetDailySnapshot> rows) {
        // Latest snapshot timestamp per key (handles carry-forward across batches).
        Map<AssetKey, java.time.LocalDateTime> latestTs = new java.util.HashMap<>();
        for (PortfolioAssetDailySnapshot row : rows) {
            if (row.getAssetType() == null || row.getAssetCode() == null || row.getMarketValueTry() == null) continue;
            if (!DerivativeSnapshotCalculator.isCountableViopRow(row)) continue;
            if (row.getCreatedAt() == null) continue;
            AssetKey key = new AssetKey(row.getAssetType().marketType(), row.getAssetCode());
            latestTs.merge(key, row.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        // SUM the market value of EVERY row at that latest timestamp. Multiple lots of the same symbol each have
        // their own per-asset row, so keeping only one undercounted the value (the cost still summed every lot)
        // → value(one lot) − cost(all lots) crashed the unfiltered chart. Single lot / carry-forward unaffected.
        Map<AssetKey, BigDecimal> result = new java.util.HashMap<>();
        for (PortfolioAssetDailySnapshot row : rows) {
            if (row.getAssetType() == null || row.getAssetCode() == null || row.getMarketValueTry() == null) continue;
            if (!DerivativeSnapshotCalculator.isCountableViopRow(row)) continue;
            AssetKey key = new AssetKey(row.getAssetType().marketType(), row.getAssetCode());
            if (row.getCreatedAt() != null && row.getCreatedAt().equals(latestTs.get(key))) {
                result.merge(key, row.getMarketValueTry(), BigDecimal::add);
            }
        }
        return result;
    }

    private void accumulateSpotPositions(List<PortfolioPosition> positions, java.time.LocalDate snapDate,
                                          Map<AssetKey, BigDecimal> rowMvByKey,
                                          Map<AssetKey, BigDecimal> prices,
                                          SnapshotTotals totals,
                                          Set<AssetKey> countedFromRows) {
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryDate() != null && pos.getEntryDate().toLocalDate().isAfter(snapDate)) continue;
            if (pos.isClosed() && !pos.getExitDate().toLocalDate().isAfter(snapDate)) {
                totals.addEntry(pos.entryValue());
                accumulateClosedSpot(pos, totals);
            } else if (accumulateOpenSpot(pos, rowMvByKey, prices, totals, countedFromRows)) {
                // Only count this lot's entry/cost when its asset was actually valued (a snapshot row or a
                // resolvable price). Counting cost while addMarket was skipped (no row AND no price) would
                // leave today's aggregate with cost-without-value, understating totalValue/PnL until the
                // next price tick — a phantom loss. Keeping cost and market on the same asset set avoids it.
                totals.addEntry(pos.entryValue());
            }
        }
    }

    private void accumulateClosedSpot(PortfolioPosition pos, SnapshotTotals totals) {
        if (pos.getExitPrice() == null) return;
        BigDecimal realized = pos.getExitPrice().subtract(pos.getEntryPrice()).multiply(pos.getQuantity());
        totals.addRealizedClose(realized, pos.getExitPrice().multiply(pos.getQuantity()));
    }

    /**
     * Adds an open position's market value to the aggregate and reports whether it could be valued.
     * Market is taken from the persisted snapshot row (deduped per asset) or, lacking a row, from a
     * resolvable price (per lot). Returns false only when the asset has neither — so the caller can also
     * skip its cost and keep totalEntry and totalValue on the same asset set.
     */
    private boolean accumulateOpenSpot(PortfolioPosition pos, Map<AssetKey, BigDecimal> rowMvByKey,
                                     Map<AssetKey, BigDecimal> prices,
                                     SnapshotTotals totals, Set<AssetKey> countedFromRows) {
        AssetKey key = pos.toAssetKey();
        BigDecimal rowMv = rowMvByKey.get(key);
        if (rowMv != null) {
            if (countedFromRows.add(key)) totals.addMarket(rowMv);
            return true;
        }
        BigDecimal price = prices.get(key);
        if (price == null) return false;
        totals.addMarket(pos.currentValue(price));
        return true;
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
                            || DerivativeSnapshotCalculator.isViopAssetType(r.getAssetType())))
                .toList();
    }
}
