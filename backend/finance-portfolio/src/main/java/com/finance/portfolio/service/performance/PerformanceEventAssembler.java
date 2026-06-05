package com.finance.portfolio.service.performance;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.dto.response.PerformanceEvent;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PerformanceEventType;
import com.finance.portfolio.model.PortfolioPosition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Detects trades (spot/derivative opens and closes) that fall in the window between two performance
 * points and turns them into {@link PerformanceEvent} markers for the chart. Add events carry the
 * entry value/notional; sell events carry the exit proceeds or realized PnL.
 */
@Component
public class PerformanceEventAssembler {

    /** Events for positions closed within the window only (sell markers), used by the CASH series. */
    public List<PerformanceEvent> realizedCloseEvents(List<PortfolioPosition> positions,
                                                      List<DerivativePosition> derivatives,
                                                      LocalDateTime prevTime, LocalDateTime currentTime) {
        TradeWindow trades = TradeWindow.detect(positions, derivatives, prevTime, currentTime);
        if (trades.spotSold().isEmpty() && trades.derivativesClosed().isEmpty()) return List.of();
        List<PerformanceEvent> events = new ArrayList<>();
        trades.spotSold().forEach(p -> {
            BigDecimal realized = p.realizedPnl();
            events.add(new PerformanceEvent(PerformanceEventType.POSITION_SOLD,
                    p.getAssetType().name(), p.getAssetCode(),
                    p.getQuantity(),
                    realized != null ? realized : BigDecimal.ZERO));
        });
        trades.derivativesClosed().forEach(d -> {
            BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
            events.add(new PerformanceEvent(PerformanceEventType.POSITION_SOLD,
                    AssetType.VIOP.name(), d.getViopContract().getSymbol(),
                    d.getQuantityLot(),
                    realized != null ? realized : BigDecimal.ZERO));
        });
        return events;
    }

    /** All add and sell events within the window; empty when nothing traded. */
    public List<PerformanceEvent> buildEvents(List<PortfolioPosition> positions,
                                               List<DerivativePosition> derivatives,
                                               LocalDateTime prevTime, LocalDateTime currentTime) {
        TradeWindow trades = TradeWindow.detect(positions, derivatives, prevTime, currentTime);
        if (trades.hasAny()) return tradeEvents(trades);
        return List.of();
    }

    public List<PerformanceEvent> tradeEvents(TradeWindow trades) {
        List<PerformanceEvent> events = new ArrayList<>();
        trades.spotAdded().forEach(p -> events.add(spotEvent(p, PerformanceEventType.POSITION_ADDED, p.entryValue())));
        trades.spotSold().forEach(p -> events.add(spotEvent(p, PerformanceEventType.POSITION_SOLD, spotProceeds(p))));
        trades.derivativesAdded().forEach(d -> events.add(derivativeEvent(
                d, PerformanceEventType.POSITION_ADDED, nullSafe(d.nominalExposure()))));
        trades.derivativesClosed().forEach(d -> events.add(derivativeEvent(
                d, PerformanceEventType.POSITION_SOLD, derivativeProceeds(d))));
        return events;
    }

    public TradeWindow detectTrades(List<PortfolioPosition> positions,
                                     List<DerivativePosition> derivatives,
                                     LocalDateTime prevTime, LocalDateTime currentTime) {
        return TradeWindow.detect(positions, derivatives, prevTime, currentTime);
    }

    private static PerformanceEvent spotEvent(PortfolioPosition pos, PerformanceEventType type, BigDecimal value) {
        return new PerformanceEvent(type, pos.getAssetType().name(), pos.getAssetCode(),
                pos.getQuantity(), value);
    }

    private static PerformanceEvent derivativeEvent(DerivativePosition d, PerformanceEventType type, BigDecimal value) {
        return new PerformanceEvent(type, AssetType.VIOP.name(), d.getViopContract().getSymbol(),
                d.getQuantityLot(), value);
    }

    private static BigDecimal spotProceeds(PortfolioPosition p) {
        return p.getExitPrice() != null ? p.getExitPrice().multiply(p.getQuantity()) : BigDecimal.ZERO;
    }

    private static BigDecimal derivativeProceeds(DerivativePosition d) {
        BigDecimal entryNotional = d.nominalExposure();
        BigDecimal realized = d.realizedOrUnrealizedPnl(d.getClosePrice());
        if (entryNotional != null && realized != null) return entryNotional.add(realized);
        return entryNotional != null ? entryNotional : BigDecimal.ZERO;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Spot/derivative opens and closes whose entry/exit date falls in {@code (prev, current]} for one chart interval. */
    public record TradeWindow(
            List<PortfolioPosition> spotAdded,
            List<PortfolioPosition> spotSold,
            List<DerivativePosition> derivativesAdded,
            List<DerivativePosition> derivativesClosed) {

        /** Buckets positions by whether their entry/exit date lands in the half-open window {@code (prev, current]}. */
        public static TradeWindow detect(List<PortfolioPosition> positions,
                                          List<DerivativePosition> derivatives,
                                          LocalDateTime prevTime, LocalDateTime currentTime) {
            return new TradeWindow(
                    inWindow(positions, PortfolioPosition::getEntryDate, prevTime, currentTime),
                    inWindow(positions, PortfolioPosition::getExitDate, prevTime, currentTime),
                    inDateWindow(derivatives, DerivativePosition::getEntryDate, prevTime, currentTime),
                    inDateWindow(derivatives, DerivativePosition::getCloseDate, prevTime, currentTime));
        }

        public boolean hasAny() {
            return !spotAdded.isEmpty() || !spotSold.isEmpty()
                    || !derivativesAdded.isEmpty() || !derivativesClosed.isEmpty();
        }

        private static List<PortfolioPosition> inWindow(List<PortfolioPosition> source,
                                                        Function<PortfolioPosition, LocalDateTime> extractor,
                                                        LocalDateTime prev, LocalDateTime curr) {
            if (source == null) return List.of();
            LocalDate prevDate = prev.toLocalDate();
            LocalDate currDate = curr.toLocalDate();
            return source.stream()
                    .filter(p -> {
                        LocalDateTime ts = extractor.apply(p);
                        if (ts == null) return false;
                        LocalDate eventDate = ts.toLocalDate();
                        return eventDate.isAfter(prevDate) && !eventDate.isAfter(currDate);
                    })
                    .toList();
        }

        private static List<DerivativePosition> inDateWindow(List<DerivativePosition> source,
                                                              Function<DerivativePosition, LocalDate> extractor,
                                                              LocalDateTime prev, LocalDateTime curr) {
            if (source == null) return List.of();
            LocalDate prevDate = prev.toLocalDate();
            LocalDate currDate = curr.toLocalDate();
            return source.stream()
                    .filter(d -> d.getViopContract() != null)
                    .filter(d -> {
                        LocalDate date = extractor.apply(d);
                        return date != null && date.isAfter(prevDate) && !date.isAfter(currDate);
                    })
                    .toList();
        }
    }
}
