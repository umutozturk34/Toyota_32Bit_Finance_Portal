package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.dto.ReportAllocation;
import com.finance.notification.reports.dto.ReportPosition;
import com.finance.notification.reports.dto.ReportSummary;
import com.finance.notification.reports.fx.ForexRatePoint;
import com.finance.notification.reports.fx.ReportFxConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Applies a {@link ReportFxConverter} to the portfolio report model — summary, positions, allocations and the
 * performance series — re-creating each DTO with its money fields converted at the correct per-field date (entry
 * date for cost basis, exit/close date for crystallised figures, as-of for "now" figures, each series point at
 * its own date). Stateless and extracted from {@link PortfolioPdfService} so the report-currency rules live in
 * one cohesive place, separate from the data-fetch + template-render orchestration.
 */
@Component
public class ReportCurrencyConverter {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    /** Joins the concurrent FX-history future, surfacing a fetch failure as a {@link PdfGenerationException}. */
    public List<ForexRatePoint> joinFxRates(CompletableFuture<List<ForexRatePoint>> fxFuture, String currency) {
        try {
            return fxFuture.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new PdfGenerationException("fx history fetch failed for " + currency, cause);
        }
    }

    /**
     * Re-creates the summary with its current money fields converted at as-of; percentages untouched. Total Cost
     * is an entry-date figure, so for a USD/EUR target it consumes the per-currency frame's already-converted
     * {@code totalEntry} (matching the position table and the on-screen SummaryCards) instead of re-converting the
     * TRY scalar at today's rate, falling back to the as-of conversion only when the frame is absent (TRY report
     * or no rates).
     */
    public ReportSummary convertSummary(ReportSummary s, ReportFxConverter fx, LocalDate asOf, String target) {
        if (s == null) return null;
        ReportSummary.ReportCurrencyFrame frame = s.frames() != null ? s.frames().get(target) : null;
        // For a USD/EUR target consume the WHOLE per-currency frame (value, cost, P&L and their %s — each
        // already converted at the right per-date FX), exactly like the on-screen SummaryCards, so the
        // document reconciles (Value − Cost = P&L) and the amount carries its own-currency return %, not the
        // TRY %. Mixing frame.totalEntry with a today-FX totalValue/totalPnl (the old behaviour) showed e.g.
        // a flat USD portfolio as +$/+% nonsense. Fall back to the as-of conversion / TRY % when no frame.
        BigDecimal totalValue = frame != null && frame.totalValue() != null
                ? frame.totalValue() : fx.convertFromTry(s.totalValueTry(), asOf);
        BigDecimal totalEntry = frame != null && frame.totalEntry() != null
                ? frame.totalEntry() : fx.convertFromTry(s.totalEntryValueTry(), asOf);
        BigDecimal totalPnl = frame != null && frame.totalPnl() != null
                ? frame.totalPnl() : fx.convertFromTry(s.totalPnlTry(), asOf);
        BigDecimal pnlPct = frame != null && frame.pnlPercent() != null
                ? frame.pnlPercent() : s.pnlPercent();
        BigDecimal dailyPnl = frame != null && frame.dailyPnl() != null
                ? frame.dailyPnl() : fx.convertFromTry(s.dailyPnlTry(), asOf);
        BigDecimal dailyPnlPct = frame != null && frame.dailyPnlPercent() != null
                ? frame.dailyPnlPercent() : s.dailyPnlPercent();
        return new ReportSummary(
                totalValue, totalEntry, totalPnl, pnlPct, dailyPnl, dailyPnlPct,
                fx.convertFromTry(s.realPnlTry(), asOf),
                s.realPnlPercent(),
                s.cpiGrowthPercent());
    }

    /**
     * Re-creates each position with money fields converted: entry price/value at the lot's ENTRY date
     * (so each lot's basis uses the rate of the day it was bought). For a CLOSED lot (it has an exit
     * date) the "current" price, exit price, market value and P&L are crystallised at the EXIT date, so
     * they use the rate of the day the lot was closed — mirroring the frontend's {@code closedFx} — not
     * today's spot. OPEN lots value their current price/market/P&L at as-of. Percentage is untouched.
     */
    public List<ReportPosition> convertPositions(List<ReportPosition> positions, ReportFxConverter fx, LocalDate asOf) {
        if (positions == null) return List.of();
        return positions.stream()
                .map(p -> {
                    LocalDate entryDate = p.entryDate() != null ? p.entryDate().toLocalDate() : asOf;
                    LocalDate currentDate = p.exitDate() != null ? p.exitDate().toLocalDate() : asOf;
                    BigDecimal entryValueConv = fx.convertFromTry(p.entryValueTry(), entryDate);
                    BigDecimal marketValueConv = fx.convertFromTry(p.marketValueTry(), currentDate);
                    // P&L = market@current-FX − cost@entry-FX (same basis as the on-screen positions table).
                    // Converting the netted pnlTry at one rate re-valued the entry cost at the current/exit
                    // FX, so the PDF disagreed with the table (e.g. -$5.69 vs -$6.767 on a closed lot).
                    // marketValueTry is direction-BLIND notional, so for a SHORT (VİOP) the raw
                    // (market − entry) carries the wrong sign — a SHORT profits when notional falls.
                    // Re-apply the direction sign so the re-derived USD/EUR P&L matches pnlTry's sign.
                    int directionSign = "SHORT".equalsIgnoreCase(p.direction()) ? -1 : 1;
                    BigDecimal pnlConv = (marketValueConv != null && entryValueConv != null)
                            ? marketValueConv.subtract(entryValueConv)
                                    .multiply(BigDecimal.valueOf(directionSign))
                            : fx.convertFromTry(p.pnlTry(), currentDate);
                    return new ReportPosition(
                            p.id(), p.assetType(), p.assetCode(), p.assetName(), p.quantity(),
                            p.entryDate(),
                            fx.convertFromTry(p.entryPrice(), entryDate),
                            p.exitDate(),
                            fx.convertFromTry(p.exitPrice(), currentDate),
                            fx.convertFromTry(p.currentPriceTry(), currentDate),
                            entryValueConv,
                            marketValueConv,
                            pnlConv,
                            p.pnlPercent(),
                            p.direction());
                })
                .toList();
    }

    /**
     * Re-creates each allocation slice with value converted at as-of (it is a "now" figure). Realized
     * P&L and its cost basis are entry/close-date figures, so for a USD/EUR target they consume the
     * per-currency frames ({@code realizedPnlByCurrency} / {@code costByCurrency}, each already converted
     * at the closed lot's own close/entry-date FX) instead of re-converting the TRY scalar at today's
     * rate; each falls back to the as-of conversion only when its frame is absent. Percent untouched.
     */
    public List<ReportAllocation> convertAllocations(List<ReportAllocation> raw, ReportFxConverter fx,
                                                     LocalDate asOf, String target) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(a -> {
                    BigDecimal realized = frameValue(a.realizedPnlByCurrency(), target);
                    BigDecimal cost = frameValue(a.costByCurrency(), target);
                    return new ReportAllocation(
                            a.label(), a.assetType(),
                            fx.convertFromTry(a.valueTry(), asOf),
                            a.percent(),
                            cost != null ? cost : fx.convertFromTry(a.costTry(), asOf),
                            realized != null ? realized : fx.convertFromTry(a.realizedPnlTry(), asOf));
                })
                .toList();
    }

    /** Per-currency frame value for {@code target}, or {@code null} when the map is missing the key. */
    private static BigDecimal frameValue(Map<String, BigDecimal> byCurrency, String target) {
        return byCurrency != null ? byCurrency.get(target) : null;
    }

    /**
     * Re-creates the value series in the report currency. Prefers the backend per-currency value frame
     * ({@code valueByCcy}: closed-lot proceeds locked at exit FX) so a fully-closed/frozen portfolio's tail
     * stays FLAT in USD/EUR — exactly like the on-screen chart. Only re-divides the flat TRY scalar by THAT
     * POINT'S OWN date FX when no per-currency value is present (TRY target, or an older payload without the
     * frame); that fallback would otherwise make the closed tail wobble with the daily rate.
     */
    public List<PerformanceSeriesPoint> convertSeries(List<PerformanceSeriesPoint> series, ReportFxConverter fx) {
        if (series == null) return List.of();
        String target = fx.target();
        return series.stream()
                .map(p -> {
                    LocalDate date = p.timestamp() != null ? p.timestamp().toLocalDate() : LocalDate.now(ISTANBUL);
                    BigDecimal locked = p.valueByCcy() != null ? p.valueByCcy().get(target) : null;
                    BigDecimal converted = locked != null ? locked : fx.convertFromTry(BigDecimal.valueOf(p.value()), date);
                    return new PerformanceSeriesPoint(p.timestamp(), converted.doubleValue());
                })
                .toList();
    }
}
