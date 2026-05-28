package com.finance.market.bond.service;

import com.finance.common.exception.BusinessException;
import com.finance.market.bond.client.EvdsBondClient;
import com.finance.market.bond.config.BondProperties;
import com.finance.market.bond.dto.external.BondSerieDto;
import com.finance.market.bond.dto.external.BondSnapshotDto;
import com.finance.market.bond.mapper.EvdsBondClientMapper;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.dto.internal.EvdsSerieResponse;
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
class BondSnapshotServiceTest {

    @Mock private EvdsBondClient evdsClient;
    @Mock private EvdsBondClientMapper clientMapper;

    private BondSnapshotService service;

    @BeforeEach
    void setUp() {
        BondProperties props = new BondProperties();
        props.setBatchSize(2);
        service = new BondSnapshotService(evdsClient, clientMapper, props);
    }

    private EvdsSerieResponse serie(String code, String name) {
        return new EvdsSerieResponse(code, name, null, "01-01-2020", "31-12-2030", "1");
    }

    private BondSnapshotDto snapshot(String series, String isin) {
        return new BondSnapshotDto(series, isin, BigDecimal.ONE, BigDecimal.ONE,
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1), "Bond " + series);
    }

    @Test
    void fetchAndFilterSeries_returnsFilteredList_whenValidSeriesFound() {
        when(evdsClient.fetchBondSerieList())
                .thenReturn(List.of(
                        serie("TP.TRT271235T17.TL.PY",
                                "5Y TRT (01.01.2025 31.12.2035)")));

        List<BondSerieDto> result = service.fetchAndFilterSeries();

        assertThat(result).isNotEmpty();
    }

    @Test
    void fetchAndFilterSeries_raises_whenFilteredEmpty() {
        when(evdsClient.fetchBondSerieList()).thenReturn(List.of());

        assertThatThrownBy(() -> service.fetchAndFilterSeries())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.market.bondNoSeriesFiltered");
    }

    @Test
    void fetchSnapshotData_collectsSnapshotsFromBatches() {
        BondSerieDto a = new BondSerieDto("ISIN1", "S1", "Bond A",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        BondSerieDto b = new BondSerieDto("ISIN2", "S2", "Bond B",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        BondSerieDto c = new BondSerieDto("ISIN3", "S3", "Bond C",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        EvdsDataResponse response = new EvdsDataResponse(1, List.of());
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString())).thenReturn(response);
        when(clientMapper.toSnapshotDtos(any(), eq(response)))
                .thenReturn(List.of(snapshot("S1", "ISIN1"), snapshot("S2", "ISIN2")))
                .thenReturn(List.of(snapshot("S3", "ISIN3")));

        List<BondSnapshotDto> result = service.fetchSnapshotData(List.of(a, b, c));

        assertThat(result).hasSize(3);
        verify(evdsClient, times(2)).fetchBondData(anyList(), anyString(), anyString());
    }

    @Test
    void fetchSnapshotData_stopsEarly_whenCircuitBreakerOpens() {
        BondSerieDto a = new BondSerieDto("ISIN1", "S1", "Bond A",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        BondSerieDto b = new BondSerieDto("ISIN2", "S2", "Bond B",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        BondSerieDto c = new BondSerieDto("ISIN3", "S3", "Bond C",
                LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
        CircuitBreaker cb = CircuitBreaker.of("evds", CircuitBreakerConfig.ofDefaults());
        when(evdsClient.fetchBondData(anyList(), anyString(), anyString()))
                .thenThrow(CallNotPermittedException.createCallNotPermittedException(cb));

        List<BondSnapshotDto> result = service.fetchSnapshotData(List.of(a, b, c));

        assertThat(result).isEmpty();
        verify(clientMapper, never()).toSnapshotDtos(any(), any());
    }

    @Test
    void fetchSnapshotData_returnsEmpty_whenInputEmpty() {
        List<BondSnapshotDto> result = service.fetchSnapshotData(List.of());

        assertThat(result).isEmpty();
        verify(evdsClient, never()).fetchBondData(anyList(), anyString(), anyString());
    }
}
