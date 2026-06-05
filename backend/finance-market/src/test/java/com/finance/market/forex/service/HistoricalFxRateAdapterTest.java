package com.finance.market.forex.service;

import com.finance.common.model.Currency;
import com.finance.market.forex.config.FxProperties;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HistoricalFxRateAdapterTest {

    @Mock private ForexCandleRepository repository;

    private FxProperties properties;
    private HistoricalFxRateAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new FxProperties();
        properties.setLookbackDays(14);
        properties.setCacheTtlMinutes(60);
        properties.setCacheMaxEntries(100);
        adapter = new HistoricalFxRateAdapter(repository, properties);
    }

    @Test
    void rateAtSameCurrencyReturnsOne() {
        Optional<BigDecimal> result = adapter.rateAt(Currency.TRY, Currency.TRY, LocalDate.of(2024, 1, 1));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("1");
        verify(repository, never()).findByCurrencyCodeOrderByCandleDateAsc("TRY");
    }

    @Test
    void rateAtUsdToTryReadsFromRepository() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(candle("USD", LocalDate.of(2024, 5, 1), "32.5")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.TRY,
                LocalDate.of(2024, 5, 5));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("32.5");
    }

    @Test
    void rateAtFallsBackToClosestPrior() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(
                        candle("USD", LocalDate.of(2024, 5, 1), "32.0"),
                        candle("USD", LocalDate.of(2024, 5, 3), "33.0")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.TRY,
                LocalDate.of(2024, 5, 10));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo("33.0");
    }

    @Test
    void rateAtReturnsEmptyWhenLookbackExceeded() {
        properties.setLookbackDays(2);
        adapter = new HistoricalFxRateAdapter(repository, properties);
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(candle("USD", LocalDate.of(2024, 5, 1), "32.0")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.TRY,
                LocalDate.of(2024, 5, 20));

        assertThat(result).isEmpty();
    }

    @Test
    void rateAtReturnsEmptyWhenNoData() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of());

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.TRY,
                LocalDate.of(2024, 1, 1));

        assertThat(result).isEmpty();
    }

    @Test
    void rateAtTryToUsdInvertsDirectRate() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(candle("USD", LocalDate.of(2024, 5, 1), "32.0")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.TRY, Currency.USD,
                LocalDate.of(2024, 5, 1));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("1").divide(new BigDecimal("32.0"),
                10, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void rateAtCrossCurrencyComputesViaTry() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(candle("USD", LocalDate.of(2024, 5, 1), "32.0")));
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("EUR"))
                .thenReturn(List.of(candle("EUR", LocalDate.of(2024, 5, 1), "35.0")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.EUR,
                LocalDate.of(2024, 5, 1));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(
                new BigDecimal("32.0").divide(new BigDecimal("35.0"), 10, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void rateAtCrossPicksUpToLegMoveOnFromLegGapDate() {
        // EUR/TRY jumps on 2024-05-02, a date with NO USD/TRY candle. The cross must reflect EUR's own
        // closest-prior move (40.0) — not freeze at the last from-leg-keyed cross point. Regression guard:
        // the cross was previously keyed only by the from-leg's dates, silently dropping to-leg-only moves.
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(
                        candle("USD", LocalDate.of(2024, 5, 1), "32.0"),
                        candle("USD", LocalDate.of(2024, 5, 3), "32.0")));
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("EUR"))
                .thenReturn(List.of(
                        candle("EUR", LocalDate.of(2024, 5, 1), "35.0"),
                        candle("EUR", LocalDate.of(2024, 5, 2), "40.0")));

        Optional<BigDecimal> result = adapter.rateAt(Currency.USD, Currency.EUR, LocalDate.of(2024, 5, 2));

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(
                new BigDecimal("32.0").divide(new BigDecimal("40.0"), 10, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void rateAtCrossReturnsEmptyWhenOneLegHasNoData() {
        // Null-leg cross: USD/TRY present, EUR/TRY absent. The missing leg's closest-prior is empty, so
        // crossViaTry emits NO entry for the date — the result is a clean "no rate" (empty), never a
        // half-converted number (e.g. the from-leg's TRY value leaking through unconverted).
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(candle("USD", LocalDate.of(2024, 5, 1), "32.0")));
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("EUR"))
                .thenReturn(List.of());

        assertThat(adapter.rateAt(Currency.USD, Currency.EUR, LocalDate.of(2024, 5, 1))).isEmpty();
    }

    @Test
    void seriesAtSameCurrencyFillsWithOnes() {
        SortedMap<LocalDate, BigDecimal> series = adapter.seriesAt(Currency.TRY, Currency.TRY,
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 3));

        assertThat(series).hasSize(3);
        assertThat(series.values()).allMatch(v -> v.compareTo(BigDecimal.ONE) == 0);
    }

    @Test
    void seriesAtFiltersToRequestedWindow() {
        lenient().when(repository.findByCurrencyCodeOrderByCandleDateAsc("USD"))
                .thenReturn(List.of(
                        candle("USD", LocalDate.of(2024, 1, 1), "30.0"),
                        candle("USD", LocalDate.of(2024, 5, 1), "32.0"),
                        candle("USD", LocalDate.of(2024, 6, 1), "33.0")));

        SortedMap<LocalDate, BigDecimal> series = adapter.seriesAt(Currency.USD, Currency.TRY,
                LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 31));

        assertThat(series).hasSize(1);
        assertThat(series.firstKey()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    void rateAtHandlesNullArguments() {
        assertThat(adapter.rateAt(null, Currency.TRY, LocalDate.of(2024, 1, 1))).isEmpty();
        assertThat(adapter.rateAt(Currency.USD, null, LocalDate.of(2024, 1, 1))).isEmpty();
        assertThat(adapter.rateAt(Currency.USD, Currency.TRY, null)).isEmpty();
    }

    private ForexCandle candle(String code, LocalDate date, String price) {
        ForexCandle c = new ForexCandle();
        c.setCurrencyCode(code);
        c.setCandleDate(LocalDateTime.of(date, LocalTime.NOON));
        c.setSellingPrice(new BigDecimal(price));
        return c;
    }
}
