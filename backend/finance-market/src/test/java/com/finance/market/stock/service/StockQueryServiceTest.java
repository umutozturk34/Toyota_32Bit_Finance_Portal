package com.finance.market.stock.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.stock.mapper.StockResponseMapper;
import com.finance.market.stock.model.StockCandle;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQueryServiceTest {

    @Mock private StockCandleRepository stockCandleRepository;
    @Mock private StockResponseMapper stockResponseMapper;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private StockQueryService service;

    @BeforeEach
    void setUp() {
        service = new StockQueryService(stockCandleRepository, stockResponseMapper, trackedAssetQueryService);
    }

    @Test
    void getMarketType_returnsStock() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.STOCK);
    }

    @Test
    void getHistory_resolvesCode_andDelegatesToRepository() {
        List<StockCandle> candles = List.of(new StockCandle());
        List<CandleResponse> mapped = List.of();
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.STOCK, "akbnk"))
                .thenReturn("AKBNK.IS");
        when(stockCandleRepository.findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("AKBNK.IS"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(candles);
        when(stockResponseMapper.toStockCandleResponses(candles)).thenReturn(mapped);

        List<CandleResponse> result = service.getHistory("akbnk", CandlePeriod.ONE_MONTH);

        assertThat(result).isSameAs(mapped);
        verify(stockResponseMapper).toStockCandleResponses(candles);
    }

    @Test
    void getHistoryInRange_passesStartAndEndOfDay() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 5);
        when(trackedAssetQueryService.resolveCodeOrThrow(TrackedAssetType.STOCK, "ASELS"))
                .thenReturn("ASELS.IS");
        when(stockCandleRepository.findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("ASELS.IS"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(stockResponseMapper.toStockCandleResponses(List.of())).thenReturn(List.of());

        service.getHistoryInRange("ASELS", from, to);

        ArgumentCaptor<LocalDateTime> fromCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stockCandleRepository).findByStockSymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("ASELS.IS"), fromCap.capture(), toCap.capture());
        assertThat(fromCap.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCap.getValue()).isEqualTo(to.atTime(LocalTime.MAX));
    }
}
