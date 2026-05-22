package com.finance.app.config;

import com.finance.common.config.AppProperties;


import com.finance.market.core.dto.response.CandleResponse;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Component
public class HistoricalPricingAdapter implements HistoricalPricingPort {

    private static final String USD_CURRENCY_CODE = "USD";

    private final int priceScale;
    private final int rateLookbackDays;
    private final Map<MarketType, MarketHistoryProvider> providers;

    public HistoricalPricingAdapter(List<MarketHistoryProvider> providerList,
                                     AppProperties appProperties,
                                     PortfolioProperties portfolioProperties) {
        this.providers = EnumDispatcher.from(MarketType.class, providerList, MarketHistoryProvider::getMarketType);
        this.priceScale = appProperties.getScale();
        this.rateLookbackDays = portfolioProperties.getHistoricalRateLookbackDays();
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
            return type == MarketType.CRYPTO ? convertCryptoUsdToTry(series, from, to) : series;
        } catch (Exception e) {
            log.warn("Failed to fetch history for {}:{} - {}", type, assetCode, e.getMessage());
            return Map.of();
        }
    }

    private Map<LocalDate, BigDecimal> convertCryptoUsdToTry(Map<LocalDate, BigDecimal> usdSeries,
                                                              LocalDate from, LocalDate to) {
        if (usdSeries.isEmpty()) return usdSeries;
        MarketHistoryProvider forexProvider = providers.get(MarketType.FOREX);
        if (forexProvider == null) {
            log.warn("Forex provider missing — crypto series stays in USD");
            return usdSeries;
        }
        Map<LocalDate, BigDecimal> rates = indexByDate(
                forexProvider.getHistoryInRange(USD_CURRENCY_CODE, from.minusDays(rateLookbackDays), to));
        if (rates.isEmpty()) {
            log.warn("{} rates empty for {}..{} — crypto series stays in USD", USD_CURRENCY_CODE, from, to);
            return usdSeries;
        }
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (var entry : usdSeries.entrySet()) {
            BigDecimal rate = closestPriorRate(rates, entry.getKey());
            BigDecimal tryPrice = PriceCrossCalculator.safeMultiply(entry.getValue(), rate, priceScale);
            if (tryPrice != null) result.put(entry.getKey(), tryPrice);
        }
        return Map.copyOf(result);
    }

    private BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> rates, LocalDate target) {
        return Stream.iterate(target, d -> d.minusDays(1))
                .limit(rateLookbackDays + 1L)
                .map(rates::get)
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
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
