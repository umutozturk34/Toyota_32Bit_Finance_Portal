package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Single entry point for analytics history, routing by instrument type to the right source: market
 * price series (via {@link HistoricalPricingPort}), macro/deposit indicator points, or bond coupon-rate
 * history. Always returns a date-ascending list and degrades to an empty list on source failure.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class UnifiedHistoryService {

    private final HistoricalPricingPort historicalPricingPort;
    private final MacroIndicatorQueryService macroQueryService;
    private final BondRateHistoryRepository bondRateHistoryRepository;

    // Short-lived memo of (instrument, window) → series. The beater/returns warm-up asks for the SAME
    // (asset, from, to) once per benchmark × currency (the raw series is currency-independent), so without
    // this every one of the ~120 (period × benchmark) combinations re-fetches and re-FX-converts the whole
    // universe — the dominant cost of the 10-minute warm. The 10-minute TTL is long enough to span a warm
    // run yet short enough that intraday requests stay fresh; a daily warm runs on a naturally-cold cache
    // (nothing queried for hours) so it always reflects the evening refresh. Empty results are never
    // retained, so a transient source failure is retried rather than pinned for the TTL.
    private final Cache<String, List<HistoryPoint>> seriesCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(6_000)
            .build();

    /**
     * Date-ascending history for the instrument over {@code [from, to]}, routed to the source for its type and
     * memoized per (instrument, window) for the cache TTL. An empty result (source failure / no data) is never
     * retained, so the next call retries.
     */
    public List<HistoryPoint> getSeries(AnalyticsInstrument instrument, LocalDate from, LocalDate to) {
        String key = instrument.type() + "|" + instrument.code() + "|" + from + "|" + to;
        List<HistoryPoint> series = seriesCache.get(key, k -> computeSeries(instrument, from, to));
        if (series.isEmpty()) {
            seriesCache.invalidate(key);
        }
        return series;
    }

    /** Drops all memoized series (e.g. after a market-data refresh) so the next fetch reloads from source. */
    public void clearCache() {
        seriesCache.invalidateAll();
    }

    private List<HistoryPoint> computeSeries(AnalyticsInstrument instrument, LocalDate from, LocalDate to) {
        if (instrument.type().isMarketBacked()) {
            return marketSeries(instrument, from, to);
        }
        return switch (instrument.type()) {
            case DEPOSIT, MACRO -> macroSeries(instrument.code(), from, to);
            case BOND -> bondSeries(instrument.code(), from, to);
            default -> {
                log.warn("No history source registered for instrument type={} code={}",
                        instrument.type(), instrument.code());
                yield List.of();
            }
        };
    }

    /**
     * Macro/deposit indicator points over {@code [from, to]} by EVDS code, bypassing the instrument cache
     * (callers pass their own probe windows); empty when the code is unknown or the fetch fails.
     */
    public List<HistoryPoint> getMacroSeries(String code, LocalDate from, LocalDate to) {
        return macroSeries(code, from, to);
    }

    private List<HistoryPoint> marketSeries(AnalyticsInstrument instrument, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> raw = historicalPricingPort.getPriceSeries(
                instrument.type().marketType(), instrument.code(), from, to);
        if (raw.isEmpty()) {
            log.debug("Empty market series type={} code={} window={}..{}",
                    instrument.type(), instrument.code(), from, to);
            return List.of();
        }
        TreeMap<LocalDate, BigDecimal> sorted = new TreeMap<>(raw);
        return sorted.entrySet().stream()
                .map(e -> new HistoryPoint(e.getKey(), e.getValue()))
                .toList();
    }

    private List<HistoryPoint> macroSeries(String code, LocalDate from, LocalDate to) {
        try {
            MacroIndicator indicator = macroQueryService.findByCode(code);
            List<MacroIndicatorPoint> points = macroQueryService.history(indicator, from, to);
            log.debug("Macro series fetched code={} window={}..{} points={}",
                    code, from, to, points.size());
            return points.stream()
                    .map(p -> new HistoryPoint(p.getObservedAt(), p.getValue()))
                    .toList();
        } catch (ResourceNotFoundException e) {
            log.warn("Macro indicator not found code={}", code);
            return List.of();
        } catch (Exception e) {
            log.error("Macro series fetch failed code={} window={}..{}: {}",
                    code, from, to, e.getMessage(), e);
            return List.of();
        }
    }

    private List<HistoryPoint> bondSeries(String isinCode, LocalDate from, LocalDate to) {
        try {
            List<BondRateHistory> full = bondRateHistoryRepository.findByIsinCodeOrderByRateDateAsc(isinCode);
            List<HistoryPoint> filtered = full.stream()
                    .filter(r -> r.getRateDate() != null && r.getCouponRate() != null)
                    .filter(r -> !r.getRateDate().isBefore(from) && !r.getRateDate().isAfter(to))
                    .map(r -> new HistoryPoint(r.getRateDate(), r.getCouponRate()))
                    .toList();
            log.debug("Bond series fetched isin={} window={}..{} points={}",
                    isinCode, from, to, filtered.size());
            return filtered;
        } catch (Exception e) {
            log.error("Bond series fetch failed isin={} window={}..{}: {}",
                    isinCode, from, to, e.getMessage(), e);
            return List.of();
        }
    }
}
