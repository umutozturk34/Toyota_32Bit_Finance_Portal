package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the entry-footprint sets the summary card feeds to {@link RealReturnCalculator} and
 * {@link MultiCurrencyPnlCalculator}: the full real-return basis (spot + Tümü/VIOP derivatives), the
 * spot-only basis (real return excludes VİOP), and the netted open-VIOP legs for the currency frames.
 * Runs inside the caller's read-only transaction (no transaction of its own).
 */
@Component
@RequiredArgsConstructor
class SummaryEntryFootprintBuilder {

    private final DerivativePositionRepository derivativePositionRepository;
    private final DerivativePricingResolver derivativePricingResolver;

    /**
     * Builds the (entryDate, entryValueTry) list that {@link RealReturnCalculator#computeFromFootprints}
     * deflates by CPI to form the real capital base. Includes spot lots (already filtered by assetType)
     * and — for Tümü or the VIOP filter — every derivative position the headline {@code lifecycleValue}
     * also covers. Asymmetry between the deflated base and the lifecycle numerator is exactly what
     * produced the previous "Reel% > Nominal%" anomaly: any lot whose value flows into the numerator
     * must also contribute to the denominator's deflated basis.
     */
    List<RealReturnCalculator.EntryFootprint> buildEntryFootprints(Long portfolioId, String assetType,
                                                                   List<PortfolioPosition> spotPositions) {
        boolean noFilter = assetType == null || assetType.isBlank();
        boolean viopOnly = !noFilter && AssetType.VIOP.name().equalsIgnoreCase(assetType);
        List<RealReturnCalculator.EntryFootprint> out =
                new ArrayList<>(buildSpotEntryFootprints(assetType, spotPositions));
        if (noFilter || viopOnly) {
            List<DerivativePosition> openDerivatives = new ArrayList<>();
            for (DerivativePosition d : derivativePositionRepository.findByPortfolioId(portfolioId)) {
                if (d.getEntryDate() == null) continue;
                BigDecimal entryNotional = d.nominalExposure();
                if (entryNotional == null) continue;
                if (d.getCloseDate() != null) {
                    // VIOP-filter numerator (applyDerivativeAdjustment) folds in open derivatives only, so
                    // the denominator must skip closed VIOP — otherwise closed-VIOP entries over-count the
                    // base and understate real %. Tümü keeps closed derivatives: its lifecycleValue DOES
                    // include closed-derivative exit value, so they belong in the base. closeDate is already a
                    // LocalDate; it freezes the base at exit CPI, matching the closed-lot exit value folded
                    // into lifecycleValue. The exit proceeds let the FX frame lock it at exit-date FX.
                    if (viopOnly) continue;
                    BigDecimal r = d.realizedOrUnrealizedPnl(d.getClosePrice());
                    BigDecimal derivExit = r != null ? r.add(entryNotional.abs()) : null;
                    // valueTry (totals.totalValue) folds in the proceeds for this lot, so exitProceedsTry = derivExit;
                    // closeNotional = price × size × lots lets the frame derive the direction-aware PnL and flip a
                    // closed SHORT's sign (proceeds@exitFX − cost@entryFX otherwise reads its profit as a loss).
                    int sign = d.getDirection() == DerivativeDirection.SHORT ? -1 : 1;
                    out.add(RealReturnCalculator.EntryFootprint.viopClosed(
                            d.getEntryDate(), entryNotional.abs(), d.getCloseDate(), derivExit, d.notionalAt(d.getClosePrice()), sign));
                } else {
                    openDerivatives.add(d);
                }
            }
            addNettedOpenViopFootprints(openDerivatives, out);
        }
        return out;
    }

    /**
     * Spot-only entry footprints (NO derivatives) — the basis for the REAL (inflation-adjusted) return, which
     * excludes VİOP. Real return measures the purchasing power of INVESTED capital; a leveraged derivative book
     * has no clean such base (its gross notional overstates the capital tied up — a net-0 hedge would show a
     * spurious real loss equal to CPI × notional — and the margin distorts the % against the nominal notional
     * basis). So VİOP is dropped here; a pure-VİOP portfolio yields no REEL row, a mixed one reports REEL on its
     * spot holdings. A per-type filter is open-only (closed lots live in the Realized section), matching the
     * open-only filtered lifecycle value its base is divided against.
     */
    List<RealReturnCalculator.EntryFootprint> buildSpotEntryFootprints(
            String assetType, List<PortfolioPosition> spotPositions) {
        boolean noFilter = assetType == null || assetType.isBlank();
        boolean viopOnly = !noFilter && AssetType.VIOP.name().equalsIgnoreCase(assetType);
        List<RealReturnCalculator.EntryFootprint> out = new ArrayList<>();
        if (viopOnly) return out;   // VİOP-filter view has no spot lots → no real-return base
        for (PortfolioPosition pos : spotPositions) {
            if (pos.getEntryDate() == null) continue;
            // A per-type filter is open-only, so its CPI basis must drop closed lots too — otherwise the
            // open-only lifecycle value would be divided by a base that still carried the closed lot.
            if (!noFilter && pos.isClosed()) continue;
            BigDecimal entryValue = pos.entryValue();
            if (entryValue == null) continue;
            // Closed lots carry their exit date so the deflated base freezes at exit CPI (deflate entry→exit);
            // open lots leave it null (deflate entry→today).
            LocalDate exit = pos.isClosed() && pos.getExitDate() != null ? pos.getExitDate().toLocalDate() : null;
            BigDecimal exitValue = (exit != null && pos.realizedPnl() != null)
                    ? pos.realizedPnl().add(entryValue) : null;
            out.add(new RealReturnCalculator.EntryFootprint(pos.getEntryDate().toLocalDate(), entryValue, exit, exitValue));
        }
        return out;
    }

    /**
     * Emits the open-derivative leg of the currency-frame basis, netting same-underlying opposite
     * directions before it reaches {@link MultiCurrencyPnlCalculator}. Legs on the same contract+entry date
     * share one entry-date FX, so they are aggregated into at most one LONG and one SHORT footprint per group
     * (instead of one per lot). Because the frame computes each footprint's direction-aware P&L as
     * {@code sign × (currentNotional/fxAt − entryNotional/fxEntry)}, the aggregated LONG and SHORT footprints
     * net linearly: a fully offsetting book (equal sizes + prices) yields {@code +(Δ) + −(Δ) = 0} in USD/EUR.
     * Aggregating per side (rather than forcing the matched portion to zero) keeps the real hedged P&L when the
     * LONG and SHORT entered at different prices. Each footprint carries its TRY notional (entry + today) so the
     * frame applies per-date FX ONCE (×FX at build, ÷FX in the frame), never twice. The summed gross entry
     * notional is preserved across both footprints, keeping the frame denominator equal to the nominal
     * {@code capitalBase} (which sums every leg's {@code entryNotional.abs()}).
     */
    void addNettedOpenViopFootprints(List<DerivativePosition> openDerivatives,
                                     List<RealReturnCalculator.EntryFootprint> out) {
        // Group by contract symbol + entry date so every leg in a group shares one entry-date FX; netting
        // legs entered on different dates (different FX) would corrupt the entry-date-FX cost basis.
        Map<String, List<DerivativePosition>> byUnderlying = new LinkedHashMap<>();
        for (DerivativePosition d : openDerivatives) {
            String symbol = d.getViopContract() != null ? d.getViopContract().getSymbol() : null;
            byUnderlying.computeIfAbsent(symbol + "|" + d.getEntryDate(), k -> new ArrayList<>()).add(d);
        }
        for (List<DerivativePosition> group : byUnderlying.values()) {
            BigDecimal longEntry = BigDecimal.ZERO;
            BigDecimal shortEntry = BigDecimal.ZERO;
            BigDecimal longCurrent = BigDecimal.ZERO;
            BigDecimal shortCurrent = BigDecimal.ZERO;
            LocalDate entryDate = group.get(0).getEntryDate();
            for (DerivativePosition d : group) {
                BigDecimal entry = d.nominalExposure().abs();
                BigDecimal current = openDerivativeNotionalTry(d);
                if (d.getDirection() == DerivativeDirection.SHORT) {
                    shortEntry = shortEntry.add(entry);
                    shortCurrent = shortCurrent.add(current);
                } else {
                    longEntry = longEntry.add(entry);
                    longCurrent = longCurrent.add(current);
                }
            }
            if (longEntry.signum() > 0) {
                out.add(RealReturnCalculator.EntryFootprint.viopOpen(entryDate, longEntry, 1, longCurrent));
            }
            if (shortEntry.signum() > 0) {
                out.add(RealReturnCalculator.EntryFootprint.viopOpen(entryDate, shortEntry, -1, shortCurrent));
            }
        }
    }

    /**
     * Current notional (live/candle price × contract size × lots) in TRY for an OPEN derivative; falls back
     * to entry notional when no live/candle price is available. Same basis as the summary's open-VIOP totals.
     */
    BigDecimal openDerivativeNotionalTry(DerivativePosition d) {
        BigDecimal contractLast = d.getViopContract().getLastPrice();
        BigDecimal liveSource = contractLast != null ? contractLast : derivativePricingResolver.latestCandleClose(d.getViopContract().getSymbol());
        BigDecimal currentTry = derivativePricingResolver.convertLiveToTry(liveSource, d.getViopContract().resolvePriceCurrency());
        BigDecimal entryNotional = d.nominalExposure();
        if (currentTry == null) return entryNotional != null ? entryNotional.abs() : BigDecimal.ZERO;
        BigDecimal contractSize = d.getViopContract().getContractSize() != null
                ? d.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = d.getQuantityLot() != null ? d.getQuantityLot() : BigDecimal.ZERO;
        return currentTry.multiply(contractSize).multiply(qty);
    }
}
