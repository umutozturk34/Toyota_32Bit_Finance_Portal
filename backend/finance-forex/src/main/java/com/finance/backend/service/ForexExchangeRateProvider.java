package com.finance.backend.service;

import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ForexExchangeRateProvider implements ExchangeRateProvider {

    private static final String USD_TRY_CODE = "USDTRY";

    private final MarketCacheService<Forex, ForexCandle> forexCacheService;

    public ForexExchangeRateProvider(MarketCacheService<Forex, ForexCandle> forexCacheService) {
        this.forexCacheService = forexCacheService;
    }

    @Override
    public ExchangeRateSnapshot getCurrentUsdTry() {
        Forex snapshot = forexCacheService.getSnapshot(USD_TRY_CODE);
        if (snapshot == null || snapshot.getCurrentPrice() == null) {
            return new ExchangeRateSnapshot(null, null);
        }
        BigDecimal current = snapshot.getCurrentPrice();
        BigDecimal previous = snapshot.getChange24h() != null
                ? current.subtract(snapshot.getChange24h())
                : current;
        return new ExchangeRateSnapshot(current, previous);
    }

    @Override
    public Map<String, YahooCandleDto> getUsdTryHistory() {
        var candles = forexCacheService.getHistory(USD_TRY_CODE);
        if (candles == null || candles.isEmpty()) {
            return Collections.emptyMap();
        }
        return candles.stream().collect(Collectors.toMap(
                c -> c.getCandleDate().toLocalDate().toString(),
                c -> new YahooCandleDto(c.getCandleDate(), c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), null),
                (a, b) -> a));
    }
}
