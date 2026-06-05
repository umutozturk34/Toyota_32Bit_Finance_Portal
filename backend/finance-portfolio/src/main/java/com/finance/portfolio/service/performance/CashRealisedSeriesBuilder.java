package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.RealReturnCalculator;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.internal.PortfolioAggregateRow;
import com.finance.portfolio.dto.response.PerformanceAssetDetail;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.dto.response.PerformancePoint;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the CASH series — cumulative realized PnL (direction-aware, live: spot realizedPnl plus a
 * derivative's realizedOrUnrealizedPnl) vs. closed cost, per snapshot date, with per-currency frames
 * locked at each lot's exit-date FX. Also exposes the realized/closed-cost helpers the aggregate path
 * reuses. Runs inside the caller's read-only transaction (no transaction of its own).
 */
@Component
@RequiredArgsConstructor
class CashRealisedSeriesBuilder {

    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PerformanceEntryFootprintBuilder footprintBuilder;
    private final PerCurrencyFrameCalculator frameCalculator;
    private final PerformanceEventAssembler eventAssembler;

    List<PerformancePoint> getCashPerformance(Long portfolioId, LocalDateTime start, LocalDateTime end) {
        List<PortfolioAggregateRow> aggregates = dailySnapshotRepository
                .findAggregateByPortfolio(portfolioId, start, end);
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(portfolioId);
        List<RealReturnCalculator.EntryFootprint> fps = footprintBuilder.footprints(positions, derivatives);
        Map<String, TreeMap<LocalDate, BigDecimal>> fxByCcy = frameCalculator.fxSeriesByCcy(fps, end.toLocalDate());

        List<PerformancePoint> result = new ArrayList<>(aggregates.size());
        LocalDateTime prevTime = start;
        for (PortfolioAggregateRow agg : aggregates) {
            LocalDate snapDate = agg.createdAt().toLocalDate();
            // Realized TRY = live, direction-aware (spot realizedPnl + derivative realizedOrUnrealizedPnl),
            // NOT the snapshot cash_try — which dropped a closed VIOP SHORT's realized, leaving the Realized
            // P/L chart flat at 0 while the card and donut (both live and direction-aware) showed the profit.
            RealizedToDate rd = realizedFor(positions, derivatives, snapDate);
            BigDecimal realized = rd.realized();
            BigDecimal closedCost = rd.closedCost();
            BigDecimal cumulativePercent = closedCost.signum() > 0
                    ? realized.multiply(new BigDecimal("100"))
                            .divide(closedCost, MoneyScale.PRICE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            List<PerformanceEvent> closeEvents = eventAssembler.realizedCloseEvents(positions, derivatives, prevTime, agg.createdAt());
            // Per-ccy realized = proceeds@exit-FX − cost@entry-FX (closed lots only) so the Realized P/L tab
            // matches the card/donut; the cumulative realized IS the value here, so value = pnl = realized.
            List<RealReturnCalculator.EntryFootprint> closedFps = fps.stream()
                    .filter(f -> f.exitDate() != null && !f.exitDate().isAfter(snapDate)).toList();
            FrameMapsR cashFrame = frameCalculator.framesForTotal(snapDate, sumClosedExit(closedFps, snapDate), closedFps, fxByCcy);
            result.add(new PerformancePoint(agg.createdAt(), realized, BigDecimal.ZERO,
                    realized, cumulativePercent, List.<PerformanceAssetDetail>of(), closeEvents,
                    cashFrame.cost(), cashFrame.pnl(), cashFrame.realized(), cashFrame.pnl()));
            prevTime = agg.createdAt();
        }
        return result;
    }

    /** Cumulative realized PnL and closed cost basis for positions/derivatives closed on or before {@code snapDate}. */
    RealizedToDate realizedFor(List<PortfolioPosition> positions,
                               List<DerivativePosition> derivatives, LocalDate snapDate) {
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal closedCost = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            if (pos.isClosed() && pos.getExitDate() != null
                    && !pos.getExitDate().toLocalDate().isAfter(snapDate)) {
                realized = realized.add(pos.realizedPnl());
                closedCost = closedCost.add(pos.entryValue());
            }
        }
        for (DerivativePosition d : derivatives) {
            if (d.getCloseDate() != null && !d.getCloseDate().isAfter(snapDate)) {
                BigDecimal r = d.realizedOrUnrealizedPnl(d.getClosePrice());
                if (r != null) realized = realized.add(r);
                BigDecimal notional = d.nominalExposure();
                if (notional != null) closedCost = closedCost.add(notional);
            }
        }
        return new RealizedToDate(realized, closedCost);
    }

    /** Sum of the type's closed-lot exit proceeds (TRY) for lots exited on or before {@code date}. */
    BigDecimal sumClosedExit(List<RealReturnCalculator.EntryFootprint> fps, LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (RealReturnCalculator.EntryFootprint fp : fps) {
            if (fp.exitDate() != null && !fp.exitDate().isAfter(date) && fp.exitValueTry() != null) {
                total = total.add(fp.exitValueTry());
            }
        }
        return total;
    }
}
