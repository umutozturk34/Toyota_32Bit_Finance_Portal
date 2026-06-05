package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativePriceResolverTest {

    private static final LocalDate TARGET = LocalDate.of(2024, 6, 1);

    @Mock private ViopCandleRepository candleRepository;
    @Mock private HistoricalPricingPort historicalPricingPort;

    private DerivativePriceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DerivativePriceResolver(candleRepository, historicalPricingPort);
    }

    private ViopContract contract(String symbol, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .currency(currency)
                .active(true)
                .build();
    }

    @Test
    void shouldReturnPriceInTry_whenContractIsTryAndCandleExists() {
        ViopContract c = contract("XU030F", "TRY");
        when(candleRepository.findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(
                eq("XU030F"), any(LocalDateTime.class)))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("110")).build()));

        BigDecimal result = resolver.resolveHistoricalPriceTry(c, TARGET);

        assertThat(result).isEqualByComparingTo("110");
    }

    @Test
    void shouldReturnNull_whenNoCandle() {
        ViopContract c = contract("XU030F", "TRY");
        when(candleRepository.findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(
                any(), any())).thenReturn(Optional.empty());

        BigDecimal result = resolver.resolveHistoricalPriceTry(c, TARGET);

        assertThat(result).isNull();
    }

    @Test
    void shouldConvertToTry_whenCurrencyIsForeignAndFxAvailable() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        when(candleRepository.findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(any(), any()))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("10")).build()));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.FOREX), eq("USD"),
                eq(TARGET.minusDays(30)), eq(TARGET)))
                .thenReturn(Map.of(TARGET, new BigDecimal("30")));

        BigDecimal result = resolver.resolveHistoricalPriceTry(c, TARGET);

        assertThat(result).isEqualByComparingTo("300");
    }

    @Test
    void shouldReturnNullWhenNoFxRate_whenCurrencyForeign() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        when(candleRepository.findFirstBySymbolAndCandleDateLessThanEqualOrderByCandleDateDesc(any(), any()))
                .thenReturn(Optional.of(ViopCandle.builder().close(new BigDecimal("10")).build()));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of());

        BigDecimal result = resolver.resolveHistoricalPriceTry(c, TARGET);

        // Foreign currency + no historical FX → null (callers must treat as "price unavailable").
        // Returning native (10) would persist USD as TRY and corrupt close_price by ~30x.
        assertThat(result).isNull();
    }

    @ParameterizedTest
    @CsvSource({"TRY", "try", "Try"})
    void shouldKeepNativePrice_whenCurrencyIsTryAnyCase(String currency) {
        BigDecimal result = resolver.nativeToTryOnDate(new BigDecimal("100"), currency, TARGET);

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void shouldReturnNullNative_whenNativePriceNull() {
        BigDecimal result = resolver.nativeToTryOnDate(null, "USD", TARGET);

        assertThat(result).isNull();
    }

    @Test
    void shouldKeepNativeWhenCurrencyBlank_whenBlankCurrency() {
        BigDecimal result = resolver.nativeToTryOnDate(new BigDecimal("100"), "", TARGET);

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void shouldKeepNativeWhenCurrencyNull_whenNullCurrency() {
        BigDecimal result = resolver.nativeToTryOnDate(new BigDecimal("100"), null, TARGET);

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void shouldUseClosestPriorRate_whenExactDateMissingButPriorExists() {
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(TARGET.minusDays(2), new BigDecimal("25")));

        BigDecimal result = resolver.nativeToTryOnDate(new BigDecimal("10"), "USD", TARGET);

        assertThat(result).isEqualByComparingTo("250");
    }

    @Test
    void shouldReturnNull_whenRateIsZero() {
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(TARGET, BigDecimal.ZERO));

        BigDecimal result = resolver.nativeToTryOnDate(new BigDecimal("10"), "USD", TARGET);

        assertThat(result).isNull();
    }
}
