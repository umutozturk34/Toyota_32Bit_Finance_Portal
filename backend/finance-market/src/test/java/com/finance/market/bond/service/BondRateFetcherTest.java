package com.finance.market.bond.service;

import com.finance.common.exception.BusinessException;
import com.finance.market.bond.client.EvdsBondClient;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondRateItemDto;
import com.finance.market.bond.mapper.EvdsBondClientMapper;
import com.finance.market.bond.model.Bond;
import com.finance.market.bond.model.BondRateHistory;
import com.finance.market.bond.repository.BondRateHistoryRepository;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BondRateFetcherTest {

    @Mock private EvdsBondClient evdsClient;
    @Mock private EvdsBondClientMapper clientMapper;
    @Mock private BondRateHistoryRepository rateHistoryRepository;

    private BondRateFetcher fetcher;

    @BeforeEach
    void setUp() {
        BondProperties props = new BondProperties();
        props.setMaxDaysPerRequest(30);
        fetcher = new BondRateFetcher(evdsClient, clientMapper, rateHistoryRepository, props);
    }

    private Bond bond(String code) {
        Bond b = new Bond();
        b.setSeriesCode(code);
        return b;
    }

    private BondRateItemDto rateItem(LocalDate date, double rate) {
        return new BondRateItemDto(date, BigDecimal.valueOf(rate));
    }

    @Test
    void fetch_returnsEmpty_whenStartNotBeforeEnd() {
        LocalDate same = LocalDate.of(2026, 5, 12);

        List<BondRateHistory> result = fetcher.fetch("TRT271235T17", bond("S1"), same, same);

        assertThat(result).isEmpty();
        verify(evdsClient, never()).fetchBondData(any(), anyString(), anyString());
    }

    @Test
    void fetch_callsEvdsForEachWindow_andMapsNewRates() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), anyString()))
                .thenReturn(List.of(rateItem(LocalDate.of(2026, 1, 5), 10.5)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("TRT271235T17"), any()))
                .thenReturn(false);

        List<BondRateHistory> result = fetcher.fetch("TRT271235T17", bond("S1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));

        assertThat(result).isNotEmpty();
    }

    @Test
    void fetch_skipsExistingDates_andRetainsNewRecordsOnly() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), anyString()))
                .thenReturn(List.of(rateItem(LocalDate.of(2026, 1, 5), 10.5),
                        rateItem(LocalDate.of(2026, 1, 6), 10.6)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("TRT271235T17"), eq(LocalDate.of(2026, 1, 5))))
                .thenReturn(true);
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("TRT271235T17"), eq(LocalDate.of(2026, 1, 6))))
                .thenReturn(false);

        List<BondRateHistory> result = fetcher.fetch("TRT271235T17", bond("S1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRateDate()).isEqualTo(LocalDate.of(2026, 1, 6));
    }

    @Test
    void fetch_propagatesCircuitBreakerException() {
        CircuitBreaker cb = CircuitBreaker.of("evds", CircuitBreakerConfig.ofDefaults());
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        assertThatThrownBy(() -> fetcher.fetch("TRT271235T17", bond("S1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void fetch_raises_whenAllWindowsFail() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("EVDS 500"));

        assertThatThrownBy(() -> fetcher.fetch("TRT271235T17", bond("S1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rate history windows failed");
    }

    @Test
    void fetch_continuesAfterSingleWindowFailure_whenOthersSucceed() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("first window flaky"))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), anyString()))
                .thenReturn(List.of(rateItem(LocalDate.of(2026, 2, 1), 10.5)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("TRT271235T17"), any())).thenReturn(false);

        List<BondRateHistory> result = fetcher.fetch("TRT271235T17", bond("S1"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));

        assertThat(result).hasSize(1);
        verify(evdsClient, times(2)).fetchBondData(anyList(), anyString(), anyString());
    }
}
