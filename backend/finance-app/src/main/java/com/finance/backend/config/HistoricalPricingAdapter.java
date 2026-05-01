package com.finance.backend.config;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.HistoricalPricingPort;
import com.finance.backend.service.MarketHistoryProvider;
import com.finance.backend.util.EnumDispatcher;
import com.finance.backend.util.SyntheticPriceCalculator;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Component
public class HistoricalPricingAdapter implements HistoricalPricingPort {

    private static final int PRICE_SCALE = 4;
    private static final int RATE_LOOKBACK_DAYS = 7;
    private static final String USD_CODE = "USDTRY";

    private final Map<MarketType, MarketHistoryProvider> providers;

    public HistoricalPricingAdapter(List<MarketHistoryProvider> providerList) {
        this.providers = EnumDispatcher.from(MarketType.class, providerList, MarketHistoryProvider::getMarketType);
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
                forexProvider.getHistoryInRange(USD_CODE, from.minusDays(RATE_LOOKBACK_DAYS), to));
        if (rates.isEmpty()) {
            log.warn("USDTRY rates empty for {}..{} — crypto series stays in USD", from, to);
            return usdSeries;
        }
        return usdSeries.entrySet().stream()
                .map(e -> Map.entry(e.getKey(),
                        SyntheticPriceCalculator.safeMultiply(e.getValue(), closestPriorRate(rates, e.getKey()), PRICE_SCALE)))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> rates, LocalDate target) {
        return Stream.iterate(target, d -> d.minusDays(1))
                .limit(RATE_LOOKBACK_DAYS + 1L)
                .map(rates::get)
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    private static Map<LocalDate, BigDecimal> indexByDate(List<?> candles) {
        return candles.stream()
                .filter(c -> candleDate(c) != null && candleClose(c) != null)
                .collect(Collectors.toUnmodifiableMap(
                        HistoricalPricingAdapter::candleDate,
                        HistoricalPricingAdapter::candleClose,
                        (a, b) -> a));
    }

    private static LocalDate candleDate(Object candle) {
        if (candle instanceof CandleResponse c) return c.candleDate().toLocalDate();
        if (candle instanceof FundCandleResponse f) return f.candleDate().toLocalDate();
        return null;
    }

    private static BigDecimal candleClose(Object candle) {
        if (candle instanceof CandleResponse c) return c.close();
        if (candle instanceof FundCandleResponse f) return f.price();
        return null;
    }
}
