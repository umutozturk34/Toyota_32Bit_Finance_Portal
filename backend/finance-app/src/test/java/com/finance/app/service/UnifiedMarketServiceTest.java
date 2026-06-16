package com.finance.app.service;

import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.common.model.StockSegment;
import com.finance.market.core.cache.TopMoversRedisService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.dto.response.MarketAvailabilityResponse;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.shared.dto.response.GroupCount;
import com.finance.shared.dto.response.StockMetadata;
import com.finance.shared.model.CandlePeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedMarketServiceTest {

    @Mock private MarketAssetProvider stockProvider;
    @Mock private MarketAssetProvider cryptoProvider;
    @Mock private MarketHistoryProvider stockHistoryProvider;
    @Mock private TopMoversRedisService topMoversRedisService;
    @Mock private HistoricalPricingPort historicalPricingPort;

    private UnifiedMarketService service;

    @BeforeEach
    void setUp() {
        when(stockProvider.getType()).thenReturn(MarketType.STOCK);
        when(cryptoProvider.getType()).thenReturn(MarketType.CRYPTO);
        when(stockHistoryProvider.getMarketType()).thenReturn(MarketType.STOCK);
        service = new UnifiedMarketService(
                List.of(stockProvider, cryptoProvider),
                List.of(stockHistoryProvider),
                topMoversRedisService, historicalPricingPort);
    }

    private MarketAssetResponse asset(String code, BigDecimal price, BigDecimal changePercent) {
        return new MarketAssetResponse(code, code, null, MarketType.STOCK,
                price, BigDecimal.ZERO, changePercent, null, null);
    }

    @Test
    void search_returnsAssetByCode_whenCodeProvided() {
        MarketAssetResponse expected = asset("THYAO", new BigDecimal("100"), BigDecimal.ONE);
        when(stockProvider.getByCode("THYAO")).thenReturn(expected);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), "THYAO", null, null, null, null, null, null, 0, 10, null, null);

        assertThat(result.content()).containsExactly(expected);
    }

    @Test
    void search_raises_whenCodeLookupFailsAcrossAllTypes() {
        when(stockProvider.getByCode("UNKNOWN")).thenReturn(null);
        when(cryptoProvider.getByCode("UNKNOWN")).thenReturn(null);

        assertThatThrownBy(() -> service.search(
                List.of(MarketType.STOCK, MarketType.CRYPTO), "UNKNOWN",
                null, null, null, null, null, null, 0, 10, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void search_swallowsProviderException_andContinuesToNextType() {
        when(stockProvider.getByCode("X")).thenThrow(new RuntimeException("boom"));
        when(cryptoProvider.getByCode("X"))
                .thenReturn(asset("X", new BigDecimal("1"), BigDecimal.ZERO));

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK, MarketType.CRYPTO), "X",
                null, null, null, null, null, null, 0, 10, null, null);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void search_returnsPagedResults_whenNoCodeProvided() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("A", new BigDecimal("1"), BigDecimal.ONE),
                        asset("B", new BigDecimal("2"), BigDecimal.TEN.negate())));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "asc", null, 0, 10, null, null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void search_usesCountBySearch_whenSearchTermProvided() {
        when(stockProvider.search(eq("THY"), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("THYAO", new BigDecimal("100"), BigDecimal.ONE)));
        when(stockProvider.countBySearch(eq("THY"), any())).thenReturn(1L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, "THY",
                "changePercent", "desc", null, 0, 10, null, null);

        assertThat(result.totalElements()).isEqualTo(1);
        verify(stockProvider).countBySearch(eq("THY"), any());
    }

    @Test
    void search_filtersToGainers_whenFilterProvided() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("UP", new BigDecimal("1"), BigDecimal.ONE),
                        asset("DOWN", new BigDecimal("2"), BigDecimal.ONE.negate())));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "gainers", 0, 10, null, null);

        assertThat(result.content()).extracting(MarketAssetResponse::code).containsExactly("UP");
    }

    @Test
    void search_filtersToLosers_whenFilterProvided() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("UP", new BigDecimal("1"), BigDecimal.ONE),
                        asset("DOWN", new BigDecimal("2"), BigDecimal.ONE.negate())));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "losers", 0, 10, null, null);

        assertThat(result.content()).extracting(MarketAssetResponse::code).containsExactly("DOWN");
    }

    @Test
    void search_keepsAll_whenFilterIsAll() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("UP", new BigDecimal("1"), BigDecimal.ONE),
                        asset("DOWN", new BigDecimal("2"), BigDecimal.ONE.negate())));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "all", 0, 10, null, null);

        assertThat(result.content()).hasSize(2);
    }

    @Test
    void search_sortsByPriceAsc_whenRequested() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("B", new BigDecimal("2"), BigDecimal.ONE),
                        asset("A", new BigDecimal("1"), BigDecimal.ONE)));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "price", "asc", null, 0, 10, null, null);

        assertThat(result.content()).extracting(MarketAssetResponse::code).containsExactly("A", "B");
    }

    @Test
    void search_sortsByName_whenRequested() {
        when(stockProvider.search(any(), any(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(List.of(asset("Z", new BigDecimal("1"), BigDecimal.ONE),
                        asset("A", new BigDecimal("2"), BigDecimal.ONE)));
        when(stockProvider.count(any())).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "name", "asc", null, 0, 10, null, null);

        assertThat(result.content()).extracting(MarketAssetResponse::code).containsExactly("A", "Z");
    }

    @Test
    void getGroupCounts_returnsEmpty_whenProviderMissing() {
        List<GroupCount> result = service.getGroupCounts(MarketType.FOREX);

        assertThat(result).isEmpty();
    }

    @Test
    void getGroupCounts_delegatesToProvider_whenAvailable() {
        when(stockProvider.getGroupCounts()).thenReturn(List.of(new GroupCount("INDEX", 5L)));

        List<GroupCount> result = service.getGroupCounts(MarketType.STOCK);

        assertThat(result).hasSize(1);
    }

    @Test
    void getHistory_raises_whenHistoryProviderMissing() {
        assertThatThrownBy(() -> service.getHistory(MarketType.FOREX, "USD", CandlePeriod.ONE_MONTH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHistory_delegatesToHistoryProvider_whenAvailable() {
        java.util.List items = List.of("candle");
        org.mockito.Mockito.doReturn(items)
                .when(stockHistoryProvider).getHistory("THYAO", CandlePeriod.ONE_MONTH);

        List<?> result = service.getHistory(MarketType.STOCK, "THYAO", CandlePeriod.ONE_MONTH);

        assertThat(result).hasSize(1);
    }

    @Test
    void getMonthlyAvailability_returnsPriceSeries_fromHistoricalPricingPort() {
        when(historicalPricingPort.getPriceSeries(eq(MarketType.STOCK), eq("THYAO"), any(), any()))
                .thenReturn(Map.of());

        MarketAvailabilityResponse result = service.getMonthlyAvailability(
                MarketType.STOCK, "THYAO", "2026-05");

        assertThat(result).isNotNull();
    }

    @Test
    void onMarketDataUpdated_writesGainersLosersAndIndices_forStockType() {
        MarketAssetResponse stockIndex = new MarketAssetResponse(
                "BIST100", "BIST 100", null, MarketType.STOCK,
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                new StockMetadata(StockSegment.MAIN_INDEX, 1000L, "BIST", null, null, null,
                        null, null, null, java.util.List.of(), java.util.List.of()));
        when(stockProvider.getTopMovers(10, true)).thenReturn(List.of());
        when(stockProvider.getTopMovers(10, false)).thenReturn(List.of());
        when(stockProvider.search(any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(stockIndex));

        service.onMarketDataUpdated(MarketType.STOCK);

        verify(topMoversRedisService).updateGainers(eq(MarketType.STOCK), any());
        verify(topMoversRedisService).updateLosers(eq(MarketType.STOCK), any());
        verify(topMoversRedisService).updateIndices(any());
    }

    @Test
    void onMarketDataUpdated_skipsIndicesUpdate_forNonStockType() {
        when(cryptoProvider.getTopMovers(10, true)).thenReturn(List.of());
        when(cryptoProvider.getTopMovers(10, false)).thenReturn(List.of());

        service.onMarketDataUpdated(MarketType.CRYPTO);

        verify(topMoversRedisService).updateGainers(eq(MarketType.CRYPTO), any());
        verify(topMoversRedisService).updateLosers(eq(MarketType.CRYPTO), any());
        verify(topMoversRedisService, never()).updateIndices(any());
    }

    @Test
    void onMarketDataUpdated_isSilent_whenProviderMissing() {
        service.onMarketDataUpdated(MarketType.FOREX);

        verify(topMoversRedisService, never()).updateGainers(any(), any());
    }

    @Test
    void onMarketDataUpdated_swallowsExceptions_silently() {
        when(stockProvider.getTopMovers(10, true)).thenThrow(new RuntimeException("Redis down"));

        service.onMarketDataUpdated(MarketType.STOCK);
    }
}
