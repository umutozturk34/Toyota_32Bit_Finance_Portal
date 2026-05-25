package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetNativeCurrencyResolver;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AnalyticsPriceSeriesProvider {

    PricedSeries fetch(AnalyticsInstrument instrument, LocalDate from, LocalDate to, Currency target);

    @Component
    @Log4j2
    class Default implements AnalyticsPriceSeriesProvider {

        private final UnifiedHistoryService historyService;

        @Autowired(required = false)
        private AssetNativeCurrencyResolver nativeCurrencyResolver;

        @Autowired(required = false)
        private CurrencyConverter currencyConverter;

        public Default(UnifiedHistoryService historyService) {
            this.historyService = historyService;
        }

        @Override
        public PricedSeries fetch(AnalyticsInstrument instrument, LocalDate from, LocalDate to, Currency target) {
            List<HistoryPoint> raw = historyService.getSeries(instrument, from, to);
            Currency effectiveTarget = target != null ? target : Currency.TRY;
            Currency nativeCurrency = resolveNative(instrument);

            if (raw == null || raw.isEmpty()) {
                return new PricedSeries(List.of(), nativeCurrency, effectiveTarget, BigDecimal.ONE, Map.of());
            }

            BigDecimal baseFx = fxAt(nativeCurrency, effectiveTarget, raw.get(0).date());
            Map<LocalDate, BigDecimal> fxMap = new HashMap<>();
            for (HistoryPoint p : raw) {
                BigDecimal fx = fxAt(nativeCurrency, effectiveTarget, p.date());
                if (fx != null) fxMap.put(p.date(), fx);
            }
            if (from != null && !fxMap.containsKey(from)) {
                BigDecimal fx = fxAt(nativeCurrency, effectiveTarget, from);
                if (fx != null) fxMap.put(from, fx);
            }
            if (to != null && !fxMap.containsKey(to)) {
                BigDecimal fx = fxAt(nativeCurrency, effectiveTarget, to);
                if (fx != null) fxMap.put(to, fx);
            }
            return new PricedSeries(raw, nativeCurrency, effectiveTarget, baseFx, fxMap);
        }

        private Currency resolveNative(AnalyticsInstrument instrument) {
            if (nativeCurrencyResolver == null) return Currency.TRY;
            MarketType marketType = mapToMarketType(instrument.type());
            Currency resolved = nativeCurrencyResolver.resolveNativeCurrency(marketType, instrument.code());
            return resolved != null ? resolved : Currency.TRY;
        }

        private MarketType mapToMarketType(AnalyticsInstrumentType type) {
            if (type.marketType() != null) return type.marketType();
            return switch (type) {
                case DEPOSIT -> MarketType.MACRO_DEPOSIT;
                case MACRO -> MarketType.MACRO_RATE;
                case BOND -> MarketType.BOND;
                default -> null;
            };
        }

        private BigDecimal fxAt(Currency from, Currency to, LocalDate date) {
            if (from == null || to == null || from == to) return BigDecimal.ONE;
            if (currencyConverter == null) {
                log.warn("CurrencyConverter not wired; falling back to 1:1 FX for {}→{} on {} — analytics frames will be wrong",
                        from, to, date);
                return BigDecimal.ONE;
            }
            try {
                return currencyConverter.convertAtDate(BigDecimal.ONE, from, to, date);
            } catch (FxRateUnavailableException e) {
                log.debug("FX unavailable {}→{} on {}, treating as 1", from, to, date);
                return null;
            }
        }
    }
}
