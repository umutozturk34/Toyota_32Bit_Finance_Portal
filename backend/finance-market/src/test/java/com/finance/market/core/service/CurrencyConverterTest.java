package com.finance.market.core.service;

import com.finance.common.model.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConverterTest {

    @Mock private FxRateProvider provider;
    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new CurrencyConverter(provider);
    }

    @Test
    void convertAtDateReturnsAmountWhenCurrenciesEqual() {
        BigDecimal amount = new BigDecimal("100");

        BigDecimal result = converter.convertAtDate(amount, Currency.TRY, Currency.TRY,
                LocalDate.of(2024, 1, 1));

        assertThat(result).isEqualByComparingTo("100");
    }

    @Test
    void convertAtDateUsesProviderRate() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        when(provider.rateAt(Currency.USD, Currency.TRY, date))
                .thenReturn(Optional.of(new BigDecimal("32.5")));

        BigDecimal result = converter.convertAtDate(new BigDecimal("100"), Currency.USD, Currency.TRY, date);

        assertThat(result).isEqualByComparingTo("3250.0000");
    }

    @Test
    void convertAtDateThrowsWhenRateMissing() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        when(provider.rateAt(Currency.USD, Currency.EUR, date)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convertAtDate(new BigDecimal("100"),
                Currency.USD, Currency.EUR, date))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    void convertAtDateReturnsNullWhenAmountNull() {
        BigDecimal result = converter.convertAtDate(null, Currency.USD, Currency.TRY,
                LocalDate.of(2024, 1, 1));

        assertThat(result).isNull();
    }

    @Test
    void rateAtReturnsOneWhenCurrenciesEqual() {
        BigDecimal result = converter.rateAt(Currency.USD, Currency.USD, LocalDate.of(2024, 1, 1));

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    void rateAtReturnsProviderRateWithoutMoneyScaleRounding() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        BigDecimal highPrecisionInverse = new BigDecimal("0.0255571932");
        when(provider.rateAt(Currency.TRY, Currency.USD, date))
                .thenReturn(Optional.of(highPrecisionInverse));

        BigDecimal result = converter.rateAt(Currency.TRY, Currency.USD, date);

        assertThat(result).isEqualByComparingTo(highPrecisionInverse);
    }

    @Test
    void rateAtThrowsWhenRateMissing() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        when(provider.rateAt(Currency.USD, Currency.EUR, date)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.rateAt(Currency.USD, Currency.EUR, date))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    void rateAtMemoizesRepeatedLookupsForSamePairAndDate() {
        LocalDate date = LocalDate.of(2024, 5, 1);
        when(provider.rateAt(Currency.USD, Currency.TRY, date)).thenReturn(Optional.of(new BigDecimal("32.5")));

        converter.rateAt(Currency.USD, Currency.TRY, date);
        converter.rateAt(Currency.USD, Currency.TRY, date);

        verify(provider, times(1)).rateAt(Currency.USD, Currency.TRY, date);
    }

    @Test
    void convertSeriesSkipsMissingDatesAndAppliesRates() {
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 2, 1);
        LocalDate d3 = LocalDate.of(2024, 3, 1);
        Map<LocalDate, BigDecimal> series = new LinkedHashMap<>();
        series.put(d1, new BigDecimal("100"));
        series.put(d2, new BigDecimal("110"));
        series.put(d3, new BigDecimal("120"));
        when(provider.rateAt(Currency.USD, Currency.TRY, d1)).thenReturn(Optional.of(new BigDecimal("30")));
        when(provider.rateAt(Currency.USD, Currency.TRY, d2)).thenReturn(Optional.empty());
        when(provider.rateAt(Currency.USD, Currency.TRY, d3)).thenReturn(Optional.of(new BigDecimal("32")));

        SortedMap<LocalDate, BigDecimal> result = converter.convertSeries(series, Currency.USD, Currency.TRY);

        assertThat(result).containsOnlyKeys(d1, d3);
        assertThat(result.get(d1)).isEqualByComparingTo("3000.0000");
        assertThat(result.get(d3)).isEqualByComparingTo("3840.0000");
    }

    @Test
    void convertSeriesReturnsCopyWhenCurrenciesEqual() {
        Map<LocalDate, BigDecimal> series = Map.of(
                LocalDate.of(2024, 1, 1), new BigDecimal("100"));

        SortedMap<LocalDate, BigDecimal> result = converter.convertSeries(series, Currency.TRY, Currency.TRY);

        assertThat(result).containsAllEntriesOf(series);
    }

    @Test
    void convertSeriesHandlesNullAndEmptyInput() {
        assertThat(converter.convertSeries(null, Currency.USD, Currency.TRY)).isEmpty();
        assertThat(converter.convertSeries(Map.of(), Currency.USD, Currency.TRY)).isEmpty();
    }
}
