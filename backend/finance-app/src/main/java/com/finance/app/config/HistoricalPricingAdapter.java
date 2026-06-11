package com.finance.app.config;

import com.finance.common.config.AppProperties;


import com.finance.common.model.Currency;
import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.service.AssetNativeCurrencyResolver;
import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.market.fund.dto.response.FundCandleResponse;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.shared.util.EnumDispatcher;
import com.finance.market.core.util.PriceCrossCalculator;
import com.finance.portfolio.config.PortfolioProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Wires the cross-module {@link HistoricalPricingPort} to the market module's per-type history providers,
 * returning each asset's daily close series in TRY. CRYPTO and foreign-quoted VIOP series (native USD/EUR
 * etc.) are converted to TRY using each date's closest-prior FX rate within a configured lookback window;
 * tether/TRY assets and commodities are already TRY and pass through unchanged.
 */
@Log4j2
@Component
public class HistoricalPricingAdapter implements HistoricalPricingPort {

    private final int priceScale;
    private final int rateLookbackDays;
    private final Map<MarketType, MarketHistoryProvider> providers;
    private final AssetNativeCurrencyResolver nativeCurrencyResolver;

    public HistoricalPricingAdapter(List<MarketHistoryProvider> providerList,
                                     AppProperties appProperties,
                                     PortfolioProperties portfolioProperties,
                                     AssetNativeCurrencyResolver nativeCurrencyResolver) {
        this.providers = EnumDispatcher.from(MarketType.class, providerList, MarketHistoryProvider::getMarketType);
        this.priceScale = appProperties.getScale();
        this.rateLookbackDays = portfolioProperties.getHistoricalRateLookbackDays();
        this.nativeCurrencyResolver = nativeCurrencyResolver;
    }

    @Override
    public Map<LocalDate, BigDecimal> getPriceSeries(MarketType type, String assetCode,
                                                     LocalDate from, LocalDate to) {
        MarketHistoryProvider provider = providers.get(type);
        if (provider == null) {
            log.warn("No history provider for market type: {}", type);
            return Map.of();
        }
        try {
            Map<LocalDate, BigDecimal> series = indexByDate(provider.getHistoryInRange(assetCode, from, to));
            if (type == MarketType.CRYPTO) {
                Currency native_ = nativeCurrencyResolver.resolveNativeCurrency(type, assetCode);
                if (native_ == Currency.TRY) return series;
                return convertNativeSeriesToTry(series, Currency.USD, from, to);
            }
            if (type == MarketType.VIOP) {
                Currency native_ = nativeCurrencyResolver.resolveNativeCurrency(type, assetCode);
                if (native_ != Currency.TRY) return convertNativeSeriesToTry(series, native_, from, to);
            }
            return series;
        } catch (Exception e) {
            log.warn("Failed to fetch history for {}:{} - {}", type, assetCode, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Converts a native-currency series to TRY by multiplying each point by the closest-prior FX rate.
     * Fetches FX from the window start minus the lookback so early points still find a prior rate; if the
     * forex provider or rates are missing, the series is left in its native currency.
     */
    private Map<LocalDate, BigDecimal> convertNativeSeriesToTry(Map<LocalDate, BigDecimal> nativeSeries,
                                                                  Currency nativeCurrency,
                                                                  LocalDate from, LocalDate to) {
        if (nativeSeries.isEmpty()) return nativeSeries;
        MarketHistoryProvider forexProvider = providers.get(MarketType.FOREX);
        if (forexProvider == null) {
            log.warn("Forex provider missing — series stays in {}", nativeCurrency);
            return nativeSeries;
        }
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>(indexByDate(
                forexProvider.getHistoryInRange(nativeCurrency.name(),
                        from.minusDays(rateLookbackDays), to)));
        if (rates.isEmpty()) {
            log.warn("{} rates empty for {}..{} — series stays in {}", nativeCurrency, from, to, nativeCurrency);
            return nativeSeries;
        }
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (var entry : nativeSeries.entrySet()) {
            BigDecimal rate = closestPriorRate(rates, entry.getKey());
            BigDecimal tryPrice = PriceCrossCalculator.safeMultiply(entry.getValue(), rate, priceScale);
            if (tryPrice != null) result.put(entry.getKey(), tryPrice);
        }
        return Map.copyOf(result);
    }

    /**
     * Most recent FX rate on or before {@code target} within the lookback window. Never uses a future/today
     * rate for a past date; returns null if the nearest prior rate is older than the lookback window. Uses
     * {@link NavigableMap#floorEntry} (O(log n)) instead of walking back day-by-day — the warm-up converts
     * thousands of points per asset, so the per-point cost dominated the run.
     */
    private BigDecimal closestPriorRate(NavigableMap<LocalDate, BigDecimal> rates, LocalDate target) {
        Map.Entry<LocalDate, BigDecimal> floor = rates.floorEntry(target);
        if (floor == null || floor.getKey().isBefore(target.minusDays(rateLookbackDays))) {
            return null;
        }
        return floor.getValue();
    }

    private static Map<LocalDate, BigDecimal> indexByDate(List<?> candles) {
        return candles.stream()
                .map(HistoricalPricingAdapter::toView)
                .filter(v -> v != null && v.date() != null && v.close() != null)
                .collect(Collectors.toUnmodifiableMap(CandleView::date, CandleView::close, (a, b) -> a));
    }

    private static CandleView toView(Object candle) {
        return switch (candle) {
            case ForexCandleResponse fx -> new CandleView(fx.candleDate().toLocalDate(), fx.sellingPrice());
            case CandleResponse c -> new CandleView(c.candleDate().toLocalDate(), c.close());
            case FundCandleResponse f -> new CandleView(f.candleDate().toLocalDate(), f.price());
            case ViopHistoryPoint v -> new CandleView(v.candleDate().toLocalDate(), v.close());
            case null, default -> null;
        };
    }

    private record CandleView(LocalDate date, BigDecimal close) { }
}
