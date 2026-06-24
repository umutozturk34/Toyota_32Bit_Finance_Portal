package com.finance.market.commodity.service;
import com.finance.market.core.service.TrackedAssetQueryService;


import com.finance.market.commodity.mapper.CommodityResponseMapper;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.core.dto.response.CandleResponse;
import com.finance.shared.model.CandlePeriod;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommodityQueryServiceTest {

    private CommodityCandleRepository candleRepository;
    private CommodityResponseMapper responseMapper;
    private TrackedAssetQueryService trackedAssetQueryService;
    private CommodityQueryService service;

    @BeforeEach
    void setUp() {
        candleRepository = mock(CommodityCandleRepository.class);
        responseMapper = mock(CommodityResponseMapper.class);
        trackedAssetQueryService = mock(TrackedAssetQueryService.class);
        service = new CommodityQueryService(candleRepository, responseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketTypeReturnsCommodity() {
        MarketType type = service.getMarketType();

        assertThat(type).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void getHistoryAllQueriesRepositoryFromEpoch() {
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        service.getHistory("GC=F", CandlePeriod.ALL);

        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
    }

    @Test
    void getHistoryWithPeriodQueriesRepository() {
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        service.getHistory("GC=F", CandlePeriod.ONE_MONTH);

        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
    }

    @Test
    void getHistoryReturnsMappedResponsesFromRepository() {
        // Arrange
        CommodityCandle candle = mock(CommodityCandle.class);
        CandleResponse mapped = new CandleResponse(
                LocalDateTime.of(2026, 1, 2, 0, 0),
                new BigDecimal("100"), new BigDecimal("110"),
                new BigDecimal("90"), new BigDecimal("105"), 5L);
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "alias"))
                .thenReturn("GC=F");
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any())).thenReturn(List.of(candle));
        when(responseMapper.toCommodityCandleResponses(List.of(candle)))
                .thenReturn(List.of(mapped));

        // Act
        List<CandleResponse> result = service.getHistory("alias", CandlePeriod.ONE_MONTH);

        // Assert: the alias is resolved and the mapper output is returned verbatim.
        assertThat(result).containsExactly(mapped);
        verify(candleRepository).findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), any(), any());
    }

    @Test
    void getHistoryInRangeQueriesRepositoryWithDayBounds() {
        // Arrange
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.COMMODITY, "GC=F"))
                .thenReturn("GC=F");
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(candleRepository.findByCommodityCodeAndCandleDateBetweenOrderByCandleDateAsc(
                eq("GC=F"), fromCaptor.capture(), toCaptor.capture())).thenReturn(List.of());
        when(responseMapper.toCommodityCandleResponses(anyList())).thenReturn(List.of());

        // Act
        List<CandleResponse> result = service.getHistoryInRange("GC=F", from, to);

        // Assert: range maps to [from start-of-day, to end-of-day].
        assertThat(result).isEmpty();
        assertThat(fromCaptor.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(to.atTime(LocalTime.MAX));
    }
}
