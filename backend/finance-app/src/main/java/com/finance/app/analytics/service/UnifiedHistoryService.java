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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public List<HistoryPoint> getSeries(AnalyticsInstrument instrument, LocalDate from, LocalDate to) {
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
