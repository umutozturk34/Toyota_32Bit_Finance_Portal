package com.finance.market.forex.service;

import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.service.ExchangeRateSnapshot;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForexExchangeRateProviderTest {

    @Mock private ForexCandleRepository forexCandleRepository;
    @SuppressWarnings("unchecked")
    private final MarketCacheService<Forex> cacheService = org.mockito.Mockito.mock(MarketCacheService.class);

    private ForexExchangeRateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ForexExchangeRateProvider(cacheService, forexCandleRepository);
    }

    @Test
    void should_returnCurrentAndPreviousFromChangeAmount_when_snapshotHasChange() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714"))
                .build();
        usd.setChangeAmount(new BigDecimal("0.0814"));
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        ExchangeRateSnapshot snapshot = provider.getCurrentUsdTry();

        assertThat(snapshot.currentRate()).isEqualByComparingTo("45.2714");
        assertThat(snapshot.previousRate()).isEqualByComparingTo("45.1900");
    }

    @Test
    void should_returnEmptySnapshot_when_cacheMisses() {
        when(cacheService.getSnapshot("USD")).thenReturn(null);

        ExchangeRateSnapshot snapshot = provider.getCurrentUsdTry();

        assertThat(snapshot.currentRate()).isNull();
        assertThat(snapshot.previousRate()).isNull();
    }

    @Test
    void should_returnEmptySnapshot_when_sellingPriceMissing() {
        Forex usd = Forex.builder().currencyCode("USD").build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        ExchangeRateSnapshot snapshot = provider.getCurrentUsdTry();

        assertThat(snapshot.currentRate()).isNull();
    }

    @Test
    void should_fallbackPreviousToCurrent_when_changeAmountMissing() {
        Forex usd = Forex.builder().currencyCode("USD")
                .sellingPrice(new BigDecimal("45.2714"))
                .build();
        when(cacheService.getSnapshot("USD")).thenReturn(usd);

        ExchangeRateSnapshot snapshot = provider.getCurrentUsdTry();

        assertThat(snapshot.currentRate()).isEqualByComparingTo("45.2714");
        assertThat(snapshot.previousRate()).isEqualByComparingTo("45.2714");
    }

    @Test
    void should_mapCandlesByDateKey_when_getUsdTryHistory() {
        LocalDateTime d1 = LocalDateTime.of(2026, 5, 10, 0, 0);
        LocalDateTime d2 = LocalDateTime.of(2026, 5, 11, 0, 0);
        ForexCandle c1 = ForexCandle.builder().currencyCode("USD").candleDate(d1)
                .sellingPrice(new BigDecimal("45.18"))
                .buyingPrice(new BigDecimal("45.10")).build();
        ForexCandle c2 = ForexCandle.builder().currencyCode("USD").candleDate(d2)
                .sellingPrice(new BigDecimal("45.27"))
                .buyingPrice(new BigDecimal("45.19")).build();
        when(forexCandleRepository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(c1, c2));

        Map<String, YahooCandleDto> history = provider.getUsdTryHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get("2026-05-10").close()).isEqualByComparingTo("45.18");
        assertThat(history.get("2026-05-11").close()).isEqualByComparingTo("45.27");
    }

    @Test
    void should_returnEmptyMap_when_noCandlesExist() {
        when(forexCandleRepository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of());

        Map<String, YahooCandleDto> history = provider.getUsdTryHistory();

        assertThat(history).isEmpty();
    }
}
