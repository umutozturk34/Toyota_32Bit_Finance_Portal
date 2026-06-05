package com.finance.portfolio.service.performance;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs a single-direction (LONG or SHORT) VIOP series from direction-blind snapshots. Snapshots
 * carry no direction column, so a same-symbol hedge's legs are split by re-marking each merged point's
 * shared contract unit price against only the requested direction's open lots, using the same
 * {@link DerivativeDirection#pnlPerLot} primitive the snapshot assembler uses. A pure transform over the
 * lists handed in — no repositories, no transaction.
 */
@Component
class ViopDirectionSeriesRecomputer {

    /** LONG/SHORT (or null when blank/unknown — never a 500), parsed leniently from the request param. */
    DerivativeDirection parseDirectionOrNull(String direction) {
        if (direction == null || direction.isBlank()) return null;
        try {
            return DerivativeDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;   // unknown value ⇒ unfiltered, never a 500
        }
    }

    /**
     * One row per createdAt for a single symbol: sums the additive legs (value=cost+signed-pnl nets a hedge's
     * opposite lots); unitPrice is shared across a symbol's lots so the first is kept.
     */
    List<PortfolioAssetDailySnapshot> mergeSameTimestampRows(List<PortfolioAssetDailySnapshot> rows) {
        Map<LocalDateTime, PortfolioAssetDailySnapshot> byTs = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot r : rows) {
            byTs.merge(r.getCreatedAt(), r, ViopDirectionSeriesRecomputer::sumAssetRows);
        }
        return new ArrayList<>(byTs.values());
    }

    /**
     * Per-direction VIOP series. Snapshots carry NO direction, so a LONG/SHORT split is reconstructed from each
     * merged point's shared contract unit price ({@code unitPriceTry}) and ONLY the requested direction's lots
     * that are open on that date — using the SAME {@link DerivativeDirection#pnlPerLot} primitive the snapshot
     * assembler uses. Because a closed lot writes no row past its close day, a closed-before-date lot correctly
     * contributes nothing, so LONG-series + SHORT-series reconstitutes the blended series for every all-open
     * instant. Daily = price move of qty held across the day (entry/close days excluded, mirroring the
     * assembler's contribution-immune daily).
     */
    List<PortfolioAssetDailySnapshot> recomputeDirectionViopSeries(
            List<PortfolioAssetDailySnapshot> mergedByTimestamp, List<DerivativePosition> directionLots) {
        List<PortfolioAssetDailySnapshot> out = new ArrayList<>(mergedByTimestamp.size());
        BigDecimal prevUnit = null;
        BigDecimal prevMarketValue = null;
        boolean prevHadLots = false;
        for (PortfolioAssetDailySnapshot snap : mergedByTimestamp) {
            BigDecimal unit = snap.getUnitPriceTry();
            LocalDate date = snap.getSnapshotDate();
            if (unit == null || date == null) { prevUnit = unit; continue; }
            BigDecimal cost = BigDecimal.ZERO;
            BigDecimal pnl = BigDecimal.ZERO;
            BigDecimal marketValue = BigDecimal.ZERO;
            BigDecimal qtyTotal = BigDecimal.ZERO;
            BigDecimal daily = BigDecimal.ZERO;
            boolean anyLot = false;
            boolean anyDaily = false;
            for (DerivativePosition d : directionLots) {
                if (d.getDirection() == null) continue;
                if (d.getEntryDate() == null || d.getEntryDate().isAfter(date)) continue;     // not opened yet
                // Skip a lot ON and after its close day: closed lots write no later rows, AND the shared merged
                // unitPrice on a close day is whichever leg merged first — using it to re-mark a different leg's
                // close (LONG and SHORT closing the same day at different prices) would mis-price it. A direction's
                // line therefore reflects its OPEN exposure and ends at its last fully-open day; the blended chart
                // (snapshot path) still carries the close-day point.
                if (d.getCloseDate() != null && !d.getCloseDate().isAfter(date)) continue;     // closed ⇒ no row
                BigDecimal size = d.getViopContract() != null && d.getViopContract().getContractSize() != null
                        ? d.getViopContract().getContractSize() : BigDecimal.ONE;
                BigDecimal qty = nz(d.getQuantityLot());
                BigDecimal entry = nz(d.getEntryPrice());
                cost = cost.add(entry.multiply(size).multiply(qty));
                pnl = pnl.add(d.getDirection().pnlPerLot(entry, unit, size).multiply(qty));
                marketValue = marketValue.add(unit.multiply(size).multiply(qty));
                qtyTotal = qtyTotal.add(qty);
                anyLot = true;
                boolean heldAcrossDay = prevUnit != null
                        && !d.getEntryDate().equals(date)
                        && (d.getCloseDate() == null || !d.getCloseDate().equals(date));
                if (heldAcrossDay) {
                    daily = daily.add(d.getDirection().pnlPerLot(prevUnit, unit, size).multiply(qty));
                    anyDaily = true;
                }
            }
            if (anyLot) {
                BigDecimal dailyPnl = anyDaily ? daily.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null;
                // % base = the PREVIOUS point's market value, but only when that point also held this direction
                // (prevHadLots): after a gap where the direction was fully closed then reopened with a different
                // size, the stale pre-gap marketValue would give a wrong %.
                BigDecimal dailyPct = (dailyPnl != null && prevHadLots && prevMarketValue != null && prevMarketValue.signum() > 0)
                        ? dailyPnl.multiply(new BigDecimal("100")).divide(prevMarketValue, MoneyScale.PRICE, RoundingMode.HALF_UP)
                        : null;
                out.add(PortfolioAssetDailySnapshot.builder()
                        .portfolioId(snap.getPortfolioId())
                        .assetType(AssetType.VIOP)
                        .assetCode(snap.getAssetCode())
                        .snapshotDate(date)
                        .createdAt(snap.getCreatedAt())
                        .quantity(qtyTotal)
                        .unitPriceTry(unit)
                        .marketValueTry(marketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                        .totalCostTry(cost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                        .pnlTry(pnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP))
                        .dailyPnlTry(dailyPnl)
                        .dailyPnlPercent(dailyPct)
                        .build());
                prevMarketValue = marketValue;
                prevHadLots = true;
            } else {
                prevHadLots = false;
            }
            prevUnit = unit;
        }
        return out;
    }

    private static PortfolioAssetDailySnapshot sumAssetRows(PortfolioAssetDailySnapshot a,
                                                            PortfolioAssetDailySnapshot b) {
        return PortfolioAssetDailySnapshot.builder()
                .portfolioId(a.getPortfolioId())
                .assetType(a.getAssetType())
                .assetCode(a.getAssetCode())
                .trackedAsset(a.getTrackedAsset())
                .snapshotDate(a.getSnapshotDate())
                .createdAt(a.getCreatedAt())
                .quantity(nz(a.getQuantity()).add(nz(b.getQuantity())))
                .unitPriceTry(a.getUnitPriceTry())
                .marketValueTry(nz(a.getMarketValueTry()).add(nz(b.getMarketValueTry())))
                .totalCostTry(nz(a.getTotalCostTry()).add(nz(b.getTotalCostTry())))
                .pnlTry(nz(a.getPnlTry()).add(nz(b.getPnlTry())))
                .dailyPnlTry(sumNullable(a.getDailyPnlTry(), b.getDailyPnlTry()))
                .dailyPnlPercent(a.getDailyPnlPercent())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.add(b);
    }
}
