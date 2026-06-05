package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thin façade over {@link DerivativeSnapshotAssembler} plus the aggregation logic that folds
 * derivative positions into a portfolio's daily totals. Closed positions use their stored close
 * price (already TRY); open ones use the contract's live last price.
 */
@Log4j2
@Service
@RequiredArgsConstructor
class DerivativeSnapshotCalculator {

    private final DerivativeSnapshotAssembler derivativeSnapshotAssembler;

    /** Snapshot for the position "as of now": stored close price when closed, else live last price. */
    PortfolioAssetDailySnapshot buildDerivativeAssetSnapshot(Long portfolioId,
                                                              DerivativePosition position,
                                                              LocalDateTime batchTimestamp) {
        if (position.getViopContract() == null) return null;
        if (!position.isOpen() && position.getClosePrice() != null) {
            return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp,
                    position.getClosePrice(), BigDecimal.ONE);
        }
        BigDecimal currentPrice = position.getViopContract().getLastPrice();
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, currentPrice);
    }

    /**
     * Value-less close-day row for a VIOP lot closed TODAY: built at closePrice with the FULL quantityLot so its
     * {@code dailyPnlTry} is the real prior-close→close move, then quantity/market/cost/pnl are zeroed so
     * {@link #isCountableViopRow} returns false. The row therefore NEVER enters the value rowMv path — its realized
     * proceeds are counted exactly once via {@link #addClosedEquity} — yet the daily K/Z card (which sums
     * dailyPnlTry regardless of countability) and the per-symbol detail series still book the close-day move.
     * Returns null when the underlying build is null (e.g. FX outage) so the caller skips the save.
     */
    PortfolioAssetDailySnapshot buildClosedViopDailyRow(Long portfolioId, DerivativePosition position,
                                                        LocalDateTime batchTimestamp) {
        PortfolioAssetDailySnapshot row = buildDerivativeAssetSnapshot(portfolioId, position, batchTimestamp);
        if (row == null) return null;
        row.setQuantity(BigDecimal.ZERO);
        row.setMarketValueTry(BigDecimal.ZERO);
        row.setTotalCostTry(BigDecimal.ZERO);
        row.setPnlTry(BigDecimal.ZERO);
        return row;
    }

    PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                DerivativePosition position,
                                                                LocalDateTime batchTimestamp,
                                                                BigDecimal exitPrice) {
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice, null);
    }

    PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                DerivativePosition position,
                                                                LocalDateTime batchTimestamp,
                                                                BigDecimal exitPrice,
                                                                BigDecimal fxRateOverride) {
        return buildDerivativeAssetSnapshotAt(portfolioId, position, batchTimestamp, exitPrice, fxRateOverride, null);
    }

    PortfolioAssetDailySnapshot buildDerivativeAssetSnapshotAt(Long portfolioId,
                                                                DerivativePosition position,
                                                                LocalDateTime batchTimestamp,
                                                                BigDecimal exitPrice,
                                                                BigDecimal fxRateOverride,
                                                                PortfolioAssetDailySnapshot priorOverride) {
        return derivativeSnapshotAssembler.buildAt(portfolioId, position, batchTimestamp,
                exitPrice, fxRateOverride, priorOverride);
    }

    /**
     * Folds each derivative into the day's {@link SnapshotTotals}, skipping lots not yet opened on
     * {@code snapDate}. Positions closed before the date contribute realized PnL; on the close day the
     * value already lives in the consolidated row (so realized is only added strictly after the close
     * day). Open lots prefer the precomputed per-symbol row market value to avoid double counting.
     */
    void accumulateDerivativePositions(List<DerivativePosition> derivatives, LocalDate snapDate,
                                        Map<AssetKey, BigDecimal> rowMvByKey,
                                        SnapshotTotals totals,
                                        Set<AssetKey> countedFromRows) {
        // Restate each open VIOP's per-symbol row market value (rowMv = notional) as EQUITY = entry +
        // sign·(notional − entry), so the snapshot PnL (= totalValue − entry) is DIRECTION-AWARE: a profiting
        // SHORT reads + instead of the backwards notional change every chart/PDF used to show. Historical
        // snapshots need each date's value, so equity is derived from the per-date rowMv via the entry-notional
        // algebra (no live price). Closed lots fold in their EQUITY proceeds (entry + realized).
        Map<AssetKey, OpenSymbol> openBySymbol = aggregateOpenSymbols(derivatives, snapDate);
        for (DerivativePosition dpos : derivatives) {
            if (dpos.getEntryDate() == null || dpos.getEntryDate().isAfter(snapDate)) continue;
            if (dpos.getViopContract() == null) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            if (entryNotional == null) continue;
            totals.addEntry(entryNotional);
            // A lot closed AS OF snapDate (the close day INCLUDED) folds in realized PROCEEDS via addClosedEquity
            // below; only TRULY OPEN lots take the rowMv (open-notional) path. Using isBefore here let a lot closed
            // ON snapDate both grab the symbol's open-row notional AND stay in aggregateOpenSymbols, doubling
            // totalLots so each lot's equity allocation was halved while the closed proceeds were dropped — exactly
            // halving a VIOP-dominated portfolio's "Tümü" value on a partial close (202k → 101k). !isAfter routes
            // the close-day lot to addClosedEquity and drops it from the open aggregate, keeping totalValue whole.
            boolean closedAsOfSnapDate = dpos.getCloseDate() != null && !dpos.getCloseDate().isAfter(snapDate);
            AssetKey key = new AssetKey(MarketType.VIOP, dpos.getViopContract().getSymbol());
            BigDecimal rowMv = rowMvByKey.get(key);
            if (rowMv != null && !closedAsOfSnapDate) {
                if (countedFromRows.add(key)) {
                    OpenSymbol agg = openBySymbol.get(key);
                    totals.addMarket(agg != null ? agg.equity(rowMv) : rowMv);
                }
                continue;
            }
            if (closedAsOfSnapDate) {
                addClosedEquity(dpos, entryNotional, totals);
            }
        }
    }

    /** A closed lot's value leg = EQUITY = entry notional + realized (= proceeds), so totalValue − entry
     *  reduces to the direction-aware realized (a profiting SHORT reads +, not the backwards notional change). */
    private void addClosedEquity(DerivativePosition dpos, BigDecimal entryNotional, SnapshotTotals totals) {
        BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
        if (realized != null) totals.addRealizedClose(realized, entryNotional.add(realized));
    }

    /** Sums open-as-of-{@code snapDate} lots per symbol: per-lot entry notional, direction sign and lot count. */
    private Map<AssetKey, OpenSymbol> aggregateOpenSymbols(List<DerivativePosition> derivatives, LocalDate snapDate) {
        Map<AssetKey, OpenSymbol> map = new java.util.HashMap<>();
        for (DerivativePosition d : derivatives) {
            if (d.getEntryDate() == null || d.getEntryDate().isAfter(snapDate)) continue;
            if (d.getViopContract() == null) continue;
            if (d.getCloseDate() != null && !d.getCloseDate().isAfter(snapDate)) continue; // open as of date only (exclude lots closed on/before snapDate)
            BigDecimal entry = d.nominalExposure();
            if (entry == null || d.getQuantityLot() == null) continue;
            int sign = d.getDirection() == com.finance.portfolio.derivative.model.DerivativeDirection.SHORT ? -1 : 1;
            map.computeIfAbsent(new AssetKey(MarketType.VIOP, d.getViopContract().getSymbol()), k -> new OpenSymbol())
                    .add(entry, sign, d.getQuantityLot());
        }
        return map;
    }

    /**
     * Per-symbol open aggregate that nets EQUITY per lot, so a symbol holding MIXED long+short lots is
     * direction-aware instead of reading the raw direction-blind notional. The symbol-level row market
     * value (rowMv = price·size·totalLots) is split across lots by lot share, then each lot contributes
     * {@code entryNotional_lot + sign_lot·(notional_lot − entryNotional_lot)}. For a uniform-direction
     * symbol this reduces to {@code entry + sign·(notional − entry)}.
     */
    private static final class OpenSymbol {
        private final List<Lot> lots = new java.util.ArrayList<>();
        private BigDecimal totalLots = BigDecimal.ZERO;

        void add(BigDecimal lotEntry, int lotSign, BigDecimal lotCount) {
            lots.add(new Lot(lotEntry, lotSign, lotCount));
            totalLots = totalLots.add(lotCount);
        }

        BigDecimal equity(BigDecimal notional) {
            if (totalLots.signum() == 0) return notional;              // no lot weights → raw notional
            BigDecimal equity = BigDecimal.ZERO;
            for (Lot lot : lots) {
                BigDecimal notionalLot = notional.multiply(lot.count).divide(totalLots, java.math.MathContext.DECIMAL64);
                BigDecimal directional = notionalLot.subtract(lot.entry).multiply(BigDecimal.valueOf(lot.sign));
                equity = equity.add(lot.entry.add(directional));
            }
            return equity;
        }

        private record Lot(BigDecimal entry, int sign, BigDecimal count) {
        }
    }

    /** Whether a row should count toward totals: non-VIOP always, VIOP only when its quantity is non-zero (excludes post-close zero rows). */
    static boolean isCountableViopRow(PortfolioAssetDailySnapshot row) {
        if (row.getAssetType() != AssetType.VIOP) return true;
        return row.getQuantity() == null || row.getQuantity().signum() != 0;
    }

    static boolean isViopAssetType(AssetType type) {
        return type == AssetType.VIOP;
    }
}
