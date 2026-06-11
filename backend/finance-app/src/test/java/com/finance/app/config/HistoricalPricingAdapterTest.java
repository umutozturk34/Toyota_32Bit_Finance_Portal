package com.finance.app.config;

import com.finance.common.config.AppProperties;
import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.service.AssetNativeCurrencyResolver;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.portfolio.config.PortfolioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoricalPricingAdapterTest {

    private MarketHistoryProvider cryptoProvider;
    private MarketHistoryProvider forexProvider;
    private AssetNativeCurrencyResolver resolver;
    private HistoricalPricingAdapter adapter;

    @BeforeEach
    void setUp() {
        cryptoProvider = mock(MarketHistoryProvider.class);
        forexProvider = mock(MarketHistoryProvider.class);
        when(cryptoProvider.getMarketType()).thenReturn(MarketType.CRYPTO);
        when(forexProvider.getMarketType()).thenReturn(MarketType.FOREX);
        resolver = mock(AssetNativeCurrencyResolver.class);
        adapter = new HistoricalPricingAdapter(List.of(cryptoProvider, forexProvider),
                new AppProperties(), new PortfolioProperties(), resolver);
    }

    private static CandleResponse candle(LocalDate date, String close) {
        return new CandleResponse(date.atStartOfDay(), null, null, null, new BigDecimal(close), null);
    }

    private static ForexCandleResponse fx(LocalDate date, String rate) {
        return new ForexCandleResponse(date.atStartOfDay(), null, null, null,
                new BigDecimal(rate), new BigDecimal(rate), null, null, null);
    }

    @Test
    void shouldConvertCryptoToTryUsingClosestPriorRateWithinLookback() {
        when(resolver.resolveNativeCurrency(MarketType.CRYPTO, "BTC")).thenReturn(Currency.USD);
        doReturn(List.of(candle(LocalDate.of(2024, 1, 11), "100")))
                .when(cryptoProvider).getHistoryInRange(eq("BTC"), any(), any());
        // No rate on the 11th; the closest prior rate (the 9th, 2 days back) is well within the 30-day lookback.
        doReturn(List.of(fx(LocalDate.of(2024, 1, 9), "30")))
                .when(forexProvider).getHistoryInRange(eq("USD"), any(), any());

        Map<LocalDate, BigDecimal> result = adapter.getPriceSeries(
                MarketType.CRYPTO, "BTC", LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 12));

        assertThat(result.get(LocalDate.of(2024, 1, 11))).isEqualByComparingTo("3000");
    }

    @Test
    void shouldDropCryptoPointWhenNearestPriorRateIsOlderThanLookback() {
        when(resolver.resolveNativeCurrency(MarketType.CRYPTO, "BTC")).thenReturn(Currency.USD);
        doReturn(List.of(candle(LocalDate.of(2024, 6, 1), "100")))
                .when(cryptoProvider).getHistoryInRange(eq("BTC"), any(), any());
        // The only rate is ~4 months before the price point — beyond the 30-day lookback, so the point is dropped.
        doReturn(List.of(fx(LocalDate.of(2024, 2, 1), "30")))
                .when(forexProvider).getHistoryInRange(eq("USD"), any(), any());

        Map<LocalDate, BigDecimal> result = adapter.getPriceSeries(
                MarketType.CRYPTO, "BTC", LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 2));

        assertThat(result).doesNotContainKey(LocalDate.of(2024, 6, 1));
    }
}
