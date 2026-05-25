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
import java.util.Map;

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

    private BondRateFetcher.BondHistoryTarget target(String isin, String priceCode, LocalDate start, LocalDate end) {
        return new BondRateFetcher.BondHistoryTarget(isin, priceCode, bond("S_" + isin), start, end);
    }

    @Test
    void fetchBatch_returnsEmpty_whenTargetsNullOrEmpty() {
        assertThat(fetcher.fetchBatch(null)).isEmpty();
        assertThat(fetcher.fetchBatch(List.of())).isEmpty();
        verify(evdsClient, never()).fetchBondData(any(), anyString(), anyString());
    }

    @Test
    void fetchBatch_skipsTargets_whenStartNotBeforeEnd() {
        LocalDate same = LocalDate.of(2026, 5, 12);
        Map<String, List<BondRateHistory>> result = fetcher.fetchBatch(List.of(target("ISINA", null, same, same)));

        assertThat(result.get("ISINA")).isEmpty();
        verify(evdsClient, never()).fetchBondData(any(), anyString(), anyString());
    }

    @Test
    void fetchBatch_combinesOranAndPriceCodesForAllTargets_inSingleEvdsCall() {
        BondRateFetcher.BondHistoryTarget t1 = target("ISINA", "TP.ISINA.TL.PY",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
        BondRateFetcher.BondHistoryTarget t2 = target("ISINB", "TP.ISINB.TL.PY",
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 15));
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), eq("TP.ISINA.ORAN"), eq("TP.ISINA.TL.PY")))
                .thenReturn(List.of(new BondRateItemDto(LocalDate.of(2026, 1, 6), new BigDecimal("10.0"), new BigDecimal("99.50"))));
        when(clientMapper.toRateItemDtos(any(), eq("TP.ISINB.ORAN"), eq("TP.ISINB.TL.PY")))
                .thenReturn(List.of(new BondRateItemDto(LocalDate.of(2026, 1, 8), new BigDecimal("12.0"), new BigDecimal("98.20"))));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(anyString(), any())).thenReturn(false);

        Map<String, List<BondRateHistory>> result = fetcher.fetchBatch(List.of(t1, t2));

        verify(evdsClient).fetchBondData(
                eq(List.of("TP.ISINA.ORAN", "TP.ISINA.TL.PY", "TP.ISINB.ORAN", "TP.ISINB.TL.PY")),
                anyString(), anyString());
        assertThat(result.get("ISINA")).hasSize(1);
        assertThat(result.get("ISINA").get(0).getPrice()).isEqualByComparingTo(new BigDecimal("99.50"));
        assertThat(result.get("ISINB")).hasSize(1);
        assertThat(result.get("ISINB").get(0).getCouponRate()).isEqualByComparingTo(new BigDecimal("12.0"));
    }

    @Test
    void fetchBatch_skipsRecordsOutsidePerTargetDateRange() {
        BondRateFetcher.BondHistoryTarget t = target("ISINA", null,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), eq("TP.ISINA.ORAN"), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of(
                        new BondRateItemDto(LocalDate.of(2026, 1, 5), new BigDecimal("10"), null),
                        new BondRateItemDto(LocalDate.of(2026, 1, 12), new BigDecimal("11"), null),
                        new BondRateItemDto(LocalDate.of(2026, 1, 25), new BigDecimal("12"), null)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(anyString(), any())).thenReturn(false);

        Map<String, List<BondRateHistory>> result = fetcher.fetchBatch(List.of(t));

        assertThat(result.get("ISINA")).hasSize(1);
        assertThat(result.get("ISINA").get(0).getRateDate()).isEqualTo(LocalDate.of(2026, 1, 12));
    }

    @Test
    void fetchBatch_skipsExistingDates_andRetainsNewRecordsOnly() {
        BondRateFetcher.BondHistoryTarget t = target("ISINA", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of(
                        new BondRateItemDto(LocalDate.of(2026, 1, 5), new BigDecimal("10"), null),
                        new BondRateItemDto(LocalDate.of(2026, 1, 6), new BigDecimal("11"), null)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISINA"), eq(LocalDate.of(2026, 1, 5)))).thenReturn(true);
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISINA"), eq(LocalDate.of(2026, 1, 6)))).thenReturn(false);

        Map<String, List<BondRateHistory>> result = fetcher.fetchBatch(List.of(t));

        assertThat(result.get("ISINA")).hasSize(1);
        assertThat(result.get("ISINA").get(0).getRateDate()).isEqualTo(LocalDate.of(2026, 1, 6));
    }

    @Test
    void fetchBatch_propagatesCircuitBreakerException() {
        CircuitBreaker cb = CircuitBreaker.of("evds", CircuitBreakerConfig.ofDefaults());
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        assertThatThrownBy(() -> fetcher.fetchBatch(List.of(
                target("ISINA", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void fetchBatch_raises_whenAllWindowsFail() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("EVDS 500"));

        assertThatThrownBy(() -> fetcher.fetchBatch(List.of(
                target("ISINA", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("batched rate history windows failed");
    }

    @Test
    void fetchBatch_continuesAfterSingleWindowFailure_whenOthersSucceed() {
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(new RuntimeException("first window flaky"))
                .thenReturn(new EvdsDataResponse(1, List.of()));
        when(clientMapper.toRateItemDtos(any(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(List.of(new BondRateItemDto(LocalDate.of(2026, 2, 1), new BigDecimal("10.5"), null)));
        when(rateHistoryRepository.existsByIsinCodeAndRateDate(eq("ISINA"), any())).thenReturn(false);

        Map<String, List<BondRateHistory>> result = fetcher.fetchBatch(List.of(
                target("ISINA", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1))));

        assertThat(result.get("ISINA")).hasSize(1);
        verify(evdsClient, times(2)).fetchBondData(anyList(), anyString(), anyString());
    }
}
