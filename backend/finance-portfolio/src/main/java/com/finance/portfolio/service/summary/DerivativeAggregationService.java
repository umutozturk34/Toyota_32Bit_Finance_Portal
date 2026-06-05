package com.finance.portfolio.service.summary;

import com.finance.portfolio.service.pricing.DerivativePricingResolver;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Folds a portfolio's derivative (VIOP) legs into the totals the summary card needs. Direction-aware:
 * open market value is the current notional (falls as a SHORT profits) while PnL is tracked separately,
 * so value − cost ≠ PnL for a SHORT. Runs inside the caller's read-only transaction.
 */
@Component
@RequiredArgsConstructor
class DerivativeAggregationService {

    private final DerivativePositionRepository derivativePositionRepository;
    private final DerivativePricingResolver derivativePricingResolver;

    /**
     * Walks the portfolio's derivative positions once and folds them into the four buckets the
     * summary card needs: open absolute MV (matches snapshot rowMv), open entry notional, open
     * unrealized PnL, and closed-lot exit cash + entry. Single pass avoids three repository hits
     * and keeps the open vs closed branching in one place so the totals stay algebraically consistent
     * with {@link SnapshotTotals} / {@link DerivativeSnapshotCalculator}.
     */
    DerivativeTotals openAndClosedDerivativeTotals(Long portfolioId) {
        DerivativeTotals totals = new DerivativeTotals();
        for (DerivativePosition position : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (position.getViopContract() == null) continue;
            BigDecimal entryNotional = position.nominalExposure();
            if (entryNotional == null) continue;
            if (position.getCloseDate() != null) {
                BigDecimal realized = position.realizedOrUnrealizedPnl(position.getClosePrice());
                BigDecimal exit = realized != null ? entryNotional.add(realized) : entryNotional;
                totals.closedEntryNotional = totals.closedEntryNotional.add(entryNotional.abs());
                totals.closedExitValue = totals.closedExitValue.add(exit);
                continue;
            }
            BigDecimal contractLast = position.getViopContract().getLastPrice();
            BigDecimal liveSource = contractLast != null
                    ? contractLast : derivativePricingResolver.latestCandleClose(position.getViopContract().getSymbol());
            BigDecimal currentTry = derivativePricingResolver.convertLiveToTry(liveSource,
                    position.getViopContract().resolvePriceCurrency());
            BigDecimal pnl = position.realizedOrUnrealizedPnl(currentTry);
            if (pnl != null) totals.openPnl = totals.openPnl.add(pnl);
            totals.openEntryNotional = totals.openEntryNotional.add(entryNotional.abs());
            // Open MV = current notional (current price × size × lots), the mark-to-market value. PnL is
            // tracked separately in openPnl and is DIRECTION-AWARE, so a profiting SHORT's notional FALLS
            // while its PnL is positive (value − cost ≠ PnL for shorts). Falls back to entry notional when
            // no live/candle price is available.
            BigDecimal contractSize = position.getViopContract().getContractSize() != null
                    ? position.getViopContract().getContractSize() : BigDecimal.ONE;
            BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
            BigDecimal mv = currentTry != null
                    ? currentTry.multiply(contractSize).multiply(qty)
                    : entryNotional.abs();
            totals.openMarketValue = totals.openMarketValue.add(mv);
        }
        return totals;
    }

    /**
     * Sum of (closed derivative exit value − closed derivative entry notional) over a portfolio's
     * closed derivative lots. Each lot's realised cash is preserved at the close date's FX via the
     * already-TRY {@code closePrice}; this method just totals them for the headline "Realised cash"
     * card so it matches the realised-PnL pie which aggregates the same closures.
     */
    BigDecimal closedDerivativeExitMinusEntry(Long portfolioId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DerivativePosition d : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (d.getCloseDate() == null) continue;
            BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
            if (realized != null) total = total.add(realized);
        }
        return total;
    }
}
