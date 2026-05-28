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
        for (DerivativePosition dpos : derivatives) {
            if (dpos.getEntryDate() == null || dpos.getEntryDate().isAfter(snapDate)) continue;
            if (dpos.getViopContract() == null) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            if (entryNotional == null) continue;
            totals.addEntry(entryNotional);
            boolean closedBeforeSnapDate = dpos.getCloseDate() != null && dpos.getCloseDate().isBefore(snapDate);
            if (closedBeforeSnapDate) {
                BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
                if (realized != null) totals.addRealizedClose(realized, entryNotional.add(realized));
                continue;
            }
            AssetKey key = new AssetKey(MarketType.VIOP, dpos.getViopContract().getSymbol());
            BigDecimal rowMv = rowMvByKey.get(key);
            if (rowMv != null) {
                if (countedFromRows.add(key)) totals.addMarket(rowMv);
                continue;
            }
            boolean closedOnSnapDate = dpos.getCloseDate() != null && dpos.getCloseDate().equals(snapDate);
            if (closedOnSnapDate) {
                BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
                if (realized != null) totals.addRealizedClose(realized, entryNotional.add(realized));
            }
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
