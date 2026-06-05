package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the per-symbol VIOP asset aggregate for the detail page, optionally scoped to one direction so a
 * same-symbol hedge's LONG/SHORT legs show as separate direction-aware K/Z cards. Mirrors the summary's
 * VIOP semantics (direction-aware PnL, raw-notional frame value) so the detail page and the card read one
 * truth. Runs inside the caller's read-only transaction.
 */
@Component
@RequiredArgsConstructor
class ViopAssetAggregateService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DerivativePositionRepository derivativePositionRepository;
    private final MultiCurrencyPnlCalculator multiCurrencyPnlCalculator;
    private final DerivativePricingResolver derivativePricingResolver;
    private final SummaryEntryFootprintBuilder summaryFootprintBuilder;

    /** Parses a LONG/SHORT direction filter; null/blank/unknown ⇒ no filter (never a 400 on a stray value). */
    DerivativeDirection parseDirectionOrNull(String direction) {
        if (direction == null || direction.isBlank()) return null;
        try {
            return DerivativeDirection.valueOf(direction.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Per-symbol VIOP aggregate, mirroring the summary VIOP-filter semantics but scoped to ONE contract.
     * Figures are DIRECTION-AWARE so a balanced LONG+SHORT hedge nets to ~0 PnL (not the direction-blind
     * value−cost): each lot contributes {@code realizedOrUnrealizedPnl(closed ? closePrice : live)}, summed
     * across open + closed lots. The frame value leg is the raw NOTIONAL MV (Σ open notional + Σ closed
     * proceeds) — exactly what the summary feeds {@code computeFromFootprints} — because the frame derives each
     * lot's direction-aware PnL as value−cost+correction and then restates value as cost+PnL; feeding it the
     * EQUITY (entry+PnL) instead would let the SHORT correction fire on a value that already baked in the
     * direction, double-counting it (a balanced hedge would read −40 in TRY, not 0). The footprints carry the
     * direction sign so the frame flips a SHORT's USD/EUR PnL.
     */
    AssetAggregateResponse viopAssetAggregate(Long portfolioId, String assetCode,
            DerivativeDirection directionFilter) {
        List<DerivativePosition> lots = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .filter(d -> d.getViopContract() != null
                        && assetCode.equalsIgnoreCase(d.getViopContract().getSymbol()))
                .filter(d -> directionFilter == null || d.getDirection() == directionFilter)
                .toList();
        if (lots.isEmpty()) {
            return new AssetAggregateResponse(
                    AssetType.VIOP.name(), assetCode, null, null,
                    0, BigDecimal.ZERO, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        }

        List<DerivativePosition> openLots = new ArrayList<>();
        List<RealReturnCalculator.EntryFootprint> footprints = new ArrayList<>();
        BigDecimal openQty = BigDecimal.ZERO;
        BigDecimal openEntryValue = BigDecimal.ZERO;   // Σ entry notional of OPEN lots (abs)
        BigDecimal openMarketValue = BigDecimal.ZERO;  // Σ open EQUITY = entryNotional + direction PnL
        BigDecimal entryBasis = BigDecimal.ZERO;       // Σ entry notional (abs) over all lots the PnL covers
        BigDecimal totalPnl = BigDecimal.ZERO;         // direction-aware PnL: a hedged book nets to ~0
        // Raw NOTIONAL value leg for the frame: Σ open notional MV + Σ closed proceeds — mirrors
        // getSummary's totals.totalValue (NOT equity). value − cost + correction is what the frame turns
        // back into the direction-aware PnL; see the Javadoc above for why equity would double-count.
        BigDecimal frameValueTry = BigDecimal.ZERO;
        LocalDateTime earliest = null;
        for (DerivativePosition d : lots) {
            BigDecimal entryNotional = d.nominalExposure();
            if (entryNotional == null) continue;
            entryBasis = entryBasis.add(entryNotional.abs());
            if (d.getEntryDate() != null) {
                LocalDateTime entryAtNoon = d.getEntryDate().atTime(12, 0);
                if (earliest == null || entryAtNoon.isBefore(earliest)) earliest = entryAtNoon;
            }
            if (d.getCloseDate() != null) {
                BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
                if (realized != null) totalPnl = totalPnl.add(realized);
                // Closed footprint identical to buildEntryFootprints' closed VIOP leg: proceeds (= entry + realized)
                // are the value folded into frameValueTry, closeNotional lets the frame flip a SHORT's sign.
                BigDecimal derivExit = realized != null ? realized.add(entryNotional.abs()) : entryNotional.abs();
                int sign = d.getDirection() == DerivativeDirection.SHORT ? -1 : 1;
                footprints.add(RealReturnCalculator.EntryFootprint.viopClosed(
                        d.getEntryDate(), entryNotional.abs(), d.getCloseDate(), derivExit,
                        d.notionalAt(d.getClosePrice()), sign));
                frameValueTry = frameValueTry.add(derivExit);
            } else {
                openLots.add(d);
                BigDecimal pnl = d.realizedOrUnrealizedPnl(liveUnitPriceTry(d));
                if (pnl != null) totalPnl = totalPnl.add(pnl);
                openQty = openQty.add(d.getQuantityLot() != null ? d.getQuantityLot() : BigDecimal.ZERO);
                openEntryValue = openEntryValue.add(entryNotional.abs());
                openMarketValue = openMarketValue.add(entryNotional.abs().add(pnl != null ? pnl : BigDecimal.ZERO));
                frameValueTry = frameValueTry.add(summaryFootprintBuilder.openDerivativeNotionalTry(d));
            }
        }
        summaryFootprintBuilder.addNettedOpenViopFootprints(openLots, footprints);

        totalPnl = totalPnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        entryBasis = entryBasis.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        frameValueTry = frameValueTry.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = entryBasis.signum() > 0
                ? totalPnl.multiply(HUNDRED).divide(entryBasis, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // currentPrice = live unit price in TRY of the symbol (one contract, all lots share it); weightedAvg
        // = entry notional per open lot-unit. Both are display-only; the frame drives the K/Z numbers.
        BigDecimal currentPrice = lots.stream()
                .filter(d -> d.getCloseDate() == null)
                .map(this::liveUnitPriceTry)
                .filter(p -> p != null)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal weightedAvg = openQty.signum() > 0
                ? openEntryValue.divide(openQty, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, CurrencyFramePct> frames = multiCurrencyPnlCalculator.computeFromFootprints(
                footprints, frameValueTry, null, pnlPercent, null);
        return new AssetAggregateResponse(
                AssetType.VIOP.name(), assetCode, assetCode, null,
                openLots.size(), openQty, earliest, weightedAvg,
                currentPrice,
                openEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                openMarketValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                totalPnl, pnlPercent, frames);
    }

    /**
     * Live unit price (contract lastPrice → latest candle close) converted to TRY; same source the open-VIOP
     * notional prices from, but the per-unit figure (no size/lot multiply).
     */
    private BigDecimal liveUnitPriceTry(DerivativePosition d) {
        BigDecimal contractLast = d.getViopContract().getLastPrice();
        BigDecimal liveSource = contractLast != null
                ? contractLast : derivativePricingResolver.latestCandleClose(d.getViopContract().getSymbol());
        return derivativePricingResolver.convertLiveToTry(liveSource, d.getViopContract().resolvePriceCurrency());
    }
}
