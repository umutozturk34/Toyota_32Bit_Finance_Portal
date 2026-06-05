package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the entry footprints (entryDate, entryValueTry, exitDate, and the direction-aware VIOP
 * extras) that drive the per-point FX cost basis in the performance charts. Spot lots carry their
 * entry/exit value; derivatives carry a direction-signed notional so a SHORT's foreign-currency K/Z
 * flips instead of reading its profit as an FX-drift loss. Operates purely on the lists handed in by
 * the caller — no repositories, no transaction.
 */
@Component
class PerformanceEntryFootprintBuilder {

    /** Entry footprints (entryDate, entryValueTry, exitDate) for spot + derivative lots — drives per-point FX cost. */
    List<RealReturnCalculator.EntryFootprint> footprints(List<PortfolioPosition> positions,
                                                         List<DerivativePosition> derivatives) {
        List<RealReturnCalculator.EntryFootprint> fps = new ArrayList<>();
        for (PortfolioPosition p : positions) {
            if (p.getEntryDate() == null || p.entryValue() == null) continue;
            LocalDate exit = p.getExitDate() != null ? p.getExitDate().toLocalDate() : null;
            BigDecimal exitValue = (exit != null && p.realizedPnl() != null)
                    ? p.realizedPnl().add(p.entryValue()) : null;
            fps.add(new RealReturnCalculator.EntryFootprint(p.getEntryDate().toLocalDate(), p.entryValue(),
                    exit, exitValue));
        }
        for (DerivativePosition d : derivatives) {
            BigDecimal notional = d.nominalExposure();
            if (d.getEntryDate() == null || notional == null) continue;
            int sign = d.getDirection() == DerivativeDirection.SHORT ? -1 : 1;
            if (d.getCloseDate() != null) {
                // Direction-aware closed footprint (mirrors perCcyInputs): exit value = proceeds (entry +
                // realized) for the value leg, closeNotional = price × size × lots so the frame can flip a
                // SHORT's foreign-currency sign instead of reading its profit as an FX-drift loss.
                BigDecimal r = d.realizedOrUnrealizedPnl(d.getClosePrice());
                BigDecimal proceeds = r != null ? r.add(notional.abs()) : notional.abs();
                fps.add(RealReturnCalculator.EntryFootprint.viopClosed(d.getEntryDate(), notional.abs(),
                        d.getCloseDate(), proceeds, d.notionalAt(d.getClosePrice()), sign));
            } else {
                // Direction-aware open footprint: sign + current notional let pointFrame add the per-date
                // correction so an open SHORT's USD/EUR K/Z flips (notional falls as it profits).
                fps.add(RealReturnCalculator.EntryFootprint.viopOpen(d.getEntryDate(), notional.abs(),
                        sign, openDerivativeNotionalTry(d)));
            }
        }
        return fps;
    }

    /**
     * Current notional (contract last price × size × lots) in TRY for an OPEN derivative when the contract is
     * TRY-quoted; falls back to the entry notional otherwise (no FX service here to convert a foreign quote).
     */
    private static BigDecimal openDerivativeNotionalTry(DerivativePosition d) {
        BigDecimal entry = d.nominalExposure();
        BigDecimal fallback = entry != null ? entry.abs() : BigDecimal.ZERO;
        if (d.getViopContract() == null || !"TRY".equals(d.getViopContract().resolvePriceCurrency())) {
            return fallback;
        }
        BigDecimal current = d.notionalAt(d.getViopContract().getLastPrice());
        return current != null ? current.abs() : fallback;
    }
}
