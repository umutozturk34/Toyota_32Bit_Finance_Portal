package com.finance.backend.config;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.HistoricalPricingPort;
import com.finance.backend.service.MarketHistoryProvider;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class HistoricalPricingAdapter implements HistoricalPricingPort {

    private final Map<MarketType, MarketHistoryProvider> providers;

    public HistoricalPricingAdapter(List<MarketHistoryProvider> providerList) {
        this.providers = new EnumMap<>(MarketType.class);
        providerList.forEach(p -> this.providers.put(p.getMarketType(), p));
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
            List<?> candles = provider.getHistoryInRange(assetCode, from, to);
            return indexByDate(candles);
        } catch (Exception e) {
            log.warn("Failed to fetch history for {}:{} - {}", type, assetCode, e.getMessage());
            return Map.of();
        }
    }

    private static Map<LocalDate, BigDecimal> indexByDate(List<?> candles) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (Object candle : candles) {
            LocalDate date = candleDate(candle);
            if (date == null) continue;
            BigDecimal close = candleClose(candle);
            if (close != null) result.put(date, close);
        }
        return result;
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
