package com.finance.portfolio.service.summary;

import com.finance.portfolio.model.MoneyScale;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts a TRY amount into per-currency "frames" (USD/EUR) at the FX rate in effect on a given date, plus the
 * realized-PnL variants that net proceeds@exit-FX against cost@entry-FX. Pure and stateless — the caller supplies
 * the pre-loaded FX rate series — extracted from {@code AllocationCalculator} so the multi-currency frame maths
 * (the same basis the summary card and chart frames use) is its own focused, testable unit.
 */
@Component
class CurrencyFrameConverter {

    /** Converts {@code realizedTry} to each frame currency at the FX rate floored to {@code date} (else the latest). */
    Map<String, BigDecimal> convertToFrames(BigDecimal realizedTry, LocalDate date,
                                            Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        if (realizedTry == null) return Collections.emptyMap();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (var entry : fxSeries.entrySet()) {
            TreeMap<LocalDate, BigDecimal> series = entry.getValue();
            if (series.isEmpty()) continue;
            BigDecimal fxRate = null;
            if (date != null) {
                var floor = series.floorEntry(date);
                if (floor != null && floor.getValue() != null && floor.getValue().signum() > 0) {
                    fxRate = floor.getValue();
                }
            }
            if (fxRate == null) {
                // No rate on/before `date` (date precedes the series, or is null): fall back to the EARLIEST
                // known rate, not the latest. lastEntry() is today's spot, which would value a years-old
                // entry/exit at the current FX and badly distort the realized-PnL frame; firstEntry() is the
                // closest historical rate to that early date.
                var earliest = series.firstEntry();
                if (earliest != null && earliest.getValue() != null && earliest.getValue().signum() > 0) {
                    fxRate = earliest.getValue();
                }
            }
            if (fxRate == null) continue;
            out.put(entry.getKey(), realizedTry.divide(fxRate, MoneyScale.PRICE, RoundingMode.HALF_UP));
        }
        return out;
    }

    /**
     * Realized PnL per currency = proceeds at the exit-date FX minus cost at the entry-date FX — the SAME
     * basis the summary card / chart frame uses ({@code MultiCurrencyPnlCalculator.pointFrame}). Converting the
     * netted TRY realized at one (exit) rate left the cost at the exit rate instead of its entry rate, so the
     * donut's "Net P/L" disagreed with the card. proceeds = realized + cost (TRY).
     */
    Map<String, BigDecimal> realizedFrames(BigDecimal proceedsTry, LocalDate exitDate,
                                           BigDecimal costTry, LocalDate entryDate,
                                           Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        Map<String, BigDecimal> proceeds = convertToFrames(proceedsTry, exitDate, fxSeries);
        Map<String, BigDecimal> cost = convertToFrames(costTry, entryDate, fxSeries);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (var e : proceeds.entrySet()) {
            BigDecimal c = cost.get(e.getKey());
            out.put(e.getKey(), c != null ? e.getValue().subtract(c) : e.getValue());
        }
        return out;
    }

    /**
     * Direction-aware realized PnL per currency for a derivative close: {@code sign × (closeNotional@closeFX −
     * cost@entryFX)}. For a LONG ({@code sign = +1}) this equals the spot-style {@code proceeds@closeFX −
     * cost@entryFX} because closeNotional == proceeds; for a SHORT ({@code sign = −1}) it negates the notional
     * delta so a profit (notional fell) reads as a profit, instead of the FX drift on the whole notional
     * dragging it negative as the proceeds formula does. closeNotional = close price × contract size × lots.
     */
    Map<String, BigDecimal> directionalRealizedFrames(BigDecimal closeNotionalTry, LocalDate closeDate,
                                                      BigDecimal costTry, LocalDate entryDate, int sign,
                                                      Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        Map<String, BigDecimal> value = convertToFrames(closeNotionalTry, closeDate, fxSeries);
        Map<String, BigDecimal> cost = convertToFrames(costTry, entryDate, fxSeries);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        BigDecimal signed = BigDecimal.valueOf(sign);
        for (var e : value.entrySet()) {
            BigDecimal c = cost.get(e.getKey());
            BigDecimal diff = c != null ? e.getValue().subtract(c) : e.getValue();
            out.put(e.getKey(), signed.multiply(diff));
        }
        return out;
    }

    /** Accumulates a per-currency frame {@code increment} into {@code target[key]}, summing matching currencies. */
    void mergeRealizedFrames(Map<String, Map<String, BigDecimal>> target, String key,
                             Map<String, BigDecimal> increment) {
        if (increment.isEmpty()) return;
        target.computeIfAbsent(key, k -> new LinkedHashMap<>());
        Map<String, BigDecimal> bucket = target.get(key);
        for (var e : increment.entrySet()) {
            bucket.merge(e.getKey(), e.getValue(), BigDecimal::add);
        }
    }
}
