package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetNativeCurrencyResolver;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.FxRateUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsPriceSeriesProviderDefaultTest {

    @Mock private UnifiedHistoryService historyService;
    @Mock private AssetNativeCurrencyResolver nativeCurrencyResolver;
    @Mock private CurrencyConverter currencyConverter;

    private AnalyticsPriceSeriesProvider.Default newProvider(boolean wireResolver, boolean wireConverter) {
        AnalyticsPriceSeriesProvider.Default provider = new AnalyticsPriceSeriesProvider.Default(historyService);
        ReflectionTestUtils.setField(provider, "nativeCurrencyResolver", wireResolver ? nativeCurrencyResolver : null);
        ReflectionTestUtils.setField(provider, "currencyConverter", wireConverter ? currencyConverter : null);
        return provider;
    }

    private static HistoryPoint point(LocalDate date, String value) {
        return new HistoryPoint(date, new BigDecimal(value));
    }

    @Test
    void shouldMemoizeFetchForSameInstrumentWindowAndTarget() {
        // Arrange
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "AAA");
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        when(historyService.getSeries(any(), any(), any()))
                .thenReturn(List.of(point(LocalDate.of(2024, 3, 1), "100")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.STOCK), eq("AAA"))).thenReturn(Currency.TRY);

        // Act
        provider.fetch(instrument, from, to, Currency.TRY);
        provider.fetch(instrument, from, to, Currency.TRY);

        // Assert
        verify(historyService, times(1)).getSeries(any(), any(), any());
    }

    @Test
    void shouldReturnEmptyPricedSeries_whenHistoryIsNull() {
        // Arrange
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "AAA");
        when(historyService.getSeries(any(), any(), any())).thenReturn(null);
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.STOCK), eq("AAA"))).thenReturn(Currency.TRY);

        // Act
        PricedSeries result = provider.fetch(instrument, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), Currency.TRY);

        // Assert
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.baseFx()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.fxByDate()).isEmpty();
    }

    @Test
    void shouldReturnEmptyPricedSeries_whenHistoryIsEmpty() {
        // Arrange
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "AAA");
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of());
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.STOCK), eq("AAA"))).thenReturn(Currency.TRY);

        // Act
        PricedSeries result = provider.fetch(instrument, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), Currency.USD);

        // Assert
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.targetCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldDefaultTargetToTry_whenTargetIsNull() {
        // Arrange — native==target==TRY means fxAt short-circuits to 1, so no converter call needed.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "AAA");
        LocalDate d = LocalDate.of(2024, 3, 1);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(d, "100")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.STOCK), eq("AAA"))).thenReturn(Currency.TRY);

        // Act
        PricedSeries result = provider.fetch(instrument, d, d, null);

        // Assert
        assertThat(result.targetCurrency()).isEqualTo(Currency.TRY);
        assertThat(result.baseFx()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.fxAt(d)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @ParameterizedTest
    @CsvSource({ "CRYPTO", "VIOP", "COMMODITY" })
    void shouldTreatPreConvertedTypesAsTryNative_withoutCallingResolver(String typeName) {
        // Arrange — these series arrive already in TRY upstream, so the native resolver must not be consulted.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrumentType type = AnalyticsInstrumentType.valueOf(typeName);
        AnalyticsInstrument instrument = new AnalyticsInstrument(type, "X");
        LocalDate d = LocalDate.of(2024, 4, 1);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(d, "50")));

        // Act
        PricedSeries result = provider.fetch(instrument, d, d, Currency.TRY);

        // Assert
        assertThat(result.nativeCurrency()).isEqualTo(Currency.TRY);
        assertThat(result.rawPoints()).hasSize(1);
    }

    @Test
    void shouldFallbackToTryNative_whenResolverIsAbsent() {
        // Arrange — resolver bean missing: a market-backed type still resolves to TRY rather than NPE.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(false, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FOREX, "USD");
        LocalDate d = LocalDate.of(2024, 5, 1);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(d, "30")));

        // Act
        PricedSeries result = provider.fetch(instrument, d, d, Currency.TRY);

        // Assert
        assertThat(result.nativeCurrency()).isEqualTo(Currency.TRY);
    }

    @Test
    void shouldFallbackToTryNative_whenResolverReturnsNull() {
        // Arrange
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "TYH");
        LocalDate d = LocalDate.of(2024, 5, 1);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(d, "12")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("TYH"))).thenReturn(null);

        // Act
        PricedSeries result = provider.fetch(instrument, d, d, Currency.TRY);

        // Assert
        assertThat(result.nativeCurrency()).isEqualTo(Currency.TRY);
    }

    @Test
    void shouldResolveNativeForRateBackedTypes_viaSwitchMapping() {
        // Arrange — BOND has no marketType() so it falls through the switch to MarketType.BOND.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.BOND, "TRT123");
        LocalDate d = LocalDate.of(2024, 6, 1);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(d, "40")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.BOND), eq("TRT123"))).thenReturn(Currency.TRY);

        // Act
        PricedSeries result = provider.fetch(instrument, d, d, Currency.TRY);

        // Assert
        assertThat(result.nativeCurrency()).isEqualTo(Currency.TRY);
        assertThat(result.fxAt(d)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void shouldBuildPerDateFxMap_whenNativeDiffersFromTarget() {
        // Arrange — USD-native fund expressed in TRY: every point gets its own date's rate.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "USDFUND");
        LocalDate first = LocalDate.of(2024, 1, 10);
        LocalDate second = LocalDate.of(2024, 2, 10);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(first, "100"), point(second, "110")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("USDFUND"))).thenReturn(Currency.USD);
        when(currencyConverter.rateAt(Currency.USD, Currency.TRY, first)).thenReturn(new BigDecimal("30"));
        when(currencyConverter.rateAt(Currency.USD, Currency.TRY, second)).thenReturn(new BigDecimal("32"));

        // Act
        PricedSeries result = provider.fetch(instrument, first, second, Currency.TRY);

        // Assert
        assertThat(result.baseFx()).isEqualByComparingTo("30");
        assertThat(result.fxAt(first)).isEqualByComparingTo("30");
        assertThat(result.fxAt(second)).isEqualByComparingTo("32");
    }

    @Test
    void shouldAddBoundaryFxRates_whenFromAndToAreNotObservationDates() {
        // Arrange — from/to fall outside the observation dates, so the boundary-FX backfill branches run.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "USDFUND");
        LocalDate obs = LocalDate.of(2024, 3, 15);
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 3, 31);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(obs, "100")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("USDFUND"))).thenReturn(Currency.USD);
        when(currencyConverter.rateAt(Currency.USD, Currency.TRY, obs)).thenReturn(new BigDecimal("30"));
        when(currencyConverter.rateAt(Currency.USD, Currency.TRY, from)).thenReturn(new BigDecimal("29"));
        when(currencyConverter.rateAt(Currency.USD, Currency.TRY, to)).thenReturn(new BigDecimal("31"));

        // Act
        PricedSeries result = provider.fetch(instrument, from, to, Currency.TRY);

        // Assert
        assertThat(result.fxAt(from)).isEqualByComparingTo("29");
        assertThat(result.fxAt(obs)).isEqualByComparingTo("30");
        assertThat(result.fxAt(to)).isEqualByComparingTo("31");
    }

    @Test
    void shouldSkipPointAndBoundaries_whenFxRateUnavailable() {
        // Arrange — converter throws for every date: fxAt returns null, so nothing is recorded in the map.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "USDFUND");
        LocalDate obs = LocalDate.of(2024, 4, 15);
        LocalDate from = LocalDate.of(2024, 4, 1);
        LocalDate to = LocalDate.of(2024, 4, 30);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(obs, "100")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("USDFUND"))).thenReturn(Currency.USD);
        when(currencyConverter.rateAt(eq(Currency.USD), eq(Currency.TRY), any()))
                .thenThrow(new FxRateUnavailableException(Currency.USD, Currency.TRY, obs));

        // Act
        PricedSeries result = provider.fetch(instrument, from, to, Currency.TRY);

        // Assert
        assertThat(result.baseFx()).isNull();
        assertThat(result.fxByDate()).isEmpty();
    }

    @Test
    void shouldFallbackToOneToOne_whenConverterBeanAbsent() {
        // Arrange — USD-native expressed in TRY but no converter wired: degrade to 1:1 instead of failing.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, false);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "USDFUND");
        LocalDate obs = LocalDate.of(2024, 5, 15);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(obs, "100")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("USDFUND"))).thenReturn(Currency.USD);

        // Act
        PricedSeries result = provider.fetch(instrument, obs, obs, Currency.TRY);

        // Assert
        assertThat(result.baseFx()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.fxAt(obs)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void shouldNotBackfillBoundaries_whenTheyAreAlreadyObservationDates() {
        // Arrange — from/to coincide with observation dates so the containsKey guards skip the extra lookups.
        AnalyticsPriceSeriesProvider.Default provider = newProvider(true, true);
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.FUND, "USDFUND");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 30);
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(point(from, "100"), point(to, "120")));
        when(nativeCurrencyResolver.resolveNativeCurrency(eq(MarketType.FUND), eq("USDFUND"))).thenReturn(Currency.USD);
        lenient().when(currencyConverter.rateAt(Currency.USD, Currency.TRY, from)).thenReturn(new BigDecimal("30"));
        lenient().when(currencyConverter.rateAt(Currency.USD, Currency.TRY, to)).thenReturn(new BigDecimal("33"));

        // Act
        PricedSeries result = provider.fetch(instrument, from, to, Currency.TRY);

        // Assert
        assertThat(result.fxByDate()).hasSize(2);
        assertThat(result.fxAt(from)).isEqualByComparingTo("30");
        assertThat(result.fxAt(to)).isEqualByComparingTo("33");
    }
}
