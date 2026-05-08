package com.finance.market.forex.service;
import com.finance.common.service.ExchangeRateProvider;

import com.finance.common.service.ExchangeRateSnapshot;

import com.finance.cache.service.MarketCacheService;


import com.finance.common.dto.external.YahooCandleDto;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.repository.ForexCandleRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ForexExchangeRateProvider implements ExchangeRateProvider {

    private static final String USD_TRY_CODE = "USDTRY";

    private final MarketCacheService<Forex> forexCacheService;
    private final ForexCandleRepository forexCandleRepository;

    public ForexExchangeRateProvider(MarketCacheService<Forex> forexCacheService,
                                     ForexCandleRepository forexCandleRepository) {
        this.forexCacheService = forexCacheService;
        this.forexCandleRepository = forexCandleRepository;
    }

    @Override
    public ExchangeRateSnapshot getCurrentUsdTry() {
        Forex snapshot = forexCacheService.getSnapshot(USD_TRY_CODE);
        if (snapshot == null || snapshot.getCurrentPrice() == null) {
            return new ExchangeRateSnapshot(null, null);
        }
        BigDecimal current = snapshot.getCurrentPrice();
        BigDecimal previous = snapshot.getChangeAmount() != null
                ? current.subtract(snapshot.getChangeAmount())
                : current;
        return new ExchangeRateSnapshot(current, previous);
    }

    @Override
    public Map<String, YahooCandleDto> getUsdTryHistory() {
        return forexCandleRepository.findByCurrencyCodeOrderByCandleDateAsc(USD_TRY_CODE).stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().toLocalDate().toString(),
                        c -> new YahooCandleDto(c.getCandleDate(), c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), null),
                        (a, b) -> a));
    }
}
