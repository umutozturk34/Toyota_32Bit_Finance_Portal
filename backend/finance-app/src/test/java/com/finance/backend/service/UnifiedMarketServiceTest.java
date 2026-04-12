package com.finance.backend.service;

import com.finance.backend.dto.response.CandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.dto.response.MarketOverviewResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.model.CandlePeriod;
import com.finance.backend.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnifiedMarketServiceTest {

    @Mock private MarketAssetProvider stockProvider;
    @Mock private MarketAssetProvider cryptoProvider;
    @Mock private TopMoversRedisService topMoversRedisService;
    @Mock private StockQueryService stockQueryService;
    @Mock private CryptoQueryService cryptoQueryService;
    @Mock private FundQueryService fundQueryService;
    @Mock private ForexQueryService forexQueryService;
    private UnifiedMarketService service;

    @BeforeEach
    void setUp() {
        lenient().when(stockProvider.getType()).thenReturn(MarketType.STOCK);
        lenient().when(cryptoProvider.getType()).thenReturn(MarketType.CRYPTO);

        service = new UnifiedMarketService(
                List.of(stockProvider, cryptoProvider),
                topMoversRedisService,
                stockQueryService,
                cryptoQueryService,
                fundQueryService,
                forexQueryService);
    }

    private MarketAssetResponse asset(String code, MarketType type, String changePercent) {
        return new MarketAssetResponse(code, code, null, type,
                new BigDecimal("100"), null,
                changePercent != null ? new BigDecimal(changePercent) : null,
                LocalDateTime.now(), null);
    }

    @Test
    void searchWithSearchTermUsesDbQuery() {
        when(stockProvider.search(eq("thy"), any(), any(), eq(0), eq(20)))
                .thenReturn(List.of(asset("THYAO.IS", MarketType.STOCK, "5.0")));
        when(stockProvider.countBySearch("thy")).thenReturn(1L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, "thy",
                "changePercent", "desc", "all", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().code()).isEqualTo("THYAO.IS");
        verify(stockProvider, never()).getAll();
    }

    @Test
    void searchPage1BypassesCache() {
        when(stockProvider.search(isNull(), any(), any(), eq(1), eq(20)))
                .thenReturn(List.of(asset("GARAN.IS", MarketType.STOCK, "3.0")));

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "all", 1, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void searchWithCodeReturnsSingleAsset() {
        MarketAssetResponse thyao = asset("THYAO.IS", MarketType.STOCK, "5.0");
        when(stockProvider.getByCode("THYAO.IS")).thenReturn(thyao);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), "THYAO.IS", null, null, null,
                null, null, null, 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void searchWithFilterGainersFiltersNegativeChange() {
        when(stockProvider.search(isNull(), eq("changePercent"), eq("desc"), eq(0), eq(20)))
                .thenReturn(List.of(
                        asset("THYAO.IS", MarketType.STOCK, "5.0"),
                        asset("GARAN.IS", MarketType.STOCK, "-2.0")));

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "gainers", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().code()).isEqualTo("THYAO.IS");
    }

    @Test
    void getOverviewUsesRedisCache() {
        when(topMoversRedisService.getIndices()).thenReturn(List.of(
                assetWithSegment("XU100", MarketType.STOCK, "1.5", "MAIN_INDEX")));
        Map<MarketType, List<MarketAssetResponse>> cached = new EnumMap<>(MarketType.class);
        cached.put(MarketType.STOCK, List.of(
                asset("THYAO.IS", MarketType.STOCK, "5.0"),
                asset("GARAN.IS", MarketType.STOCK, "-3.0")));
        when(topMoversRedisService.getAllMovers()).thenReturn(cached);

        MarketOverviewResponse overview = service.getOverview(5);

        assertThat(overview.indices()).hasSize(1);
        assertThat(overview.movers()).hasSize(1);
        assertThat(overview.movers().getFirst().type()).isEqualTo("STOCK");
        assertThat(overview.movers().getFirst().gainers()).hasSize(1);
        assertThat(overview.movers().getFirst().losers()).hasSize(1);
    }

    @Test
    void getOverviewFallsBackToProvidersWhenCacheEmpty() {
        when(topMoversRedisService.getAllMovers()).thenReturn(Map.of());
        when(stockProvider.getTopMovers(5, true))
                .thenReturn(List.of(asset("THYAO.IS", MarketType.STOCK, "5.0")));
        when(stockProvider.getTopMovers(5, false))
                .thenReturn(List.of(asset("GARAN.IS", MarketType.STOCK, "-3.0")));
        when(cryptoProvider.getTopMovers(5, true)).thenReturn(List.of());
        when(cryptoProvider.getTopMovers(5, false)).thenReturn(List.of());

        MarketOverviewResponse overview = service.getOverview(5);

        assertThat(overview.movers()).hasSize(1);
        assertThat(overview.movers().getFirst().gainers()).hasSize(1);
        assertThat(overview.movers().getFirst().losers()).hasSize(1);
    }

    @Test
    void getHistoryDelegatesToStockQueryService() {
        List<CandleResponse> candles = List.of(
                new CandleResponse(LocalDateTime.now(), null, null, null, new BigDecimal("50"), null));
        when(stockQueryService.getStockHistory("THYAO.IS", CandlePeriod.ONE_MONTH)).thenReturn(candles);

        List<?> result = service.getHistory(MarketType.STOCK, "THYAO.IS", CandlePeriod.ONE_MONTH);

        assertThat(result).hasSize(1);
        verify(stockQueryService).getStockHistory("THYAO.IS", CandlePeriod.ONE_MONTH);
    }

    @Test
    void getHistoryDelegatesToCryptoQueryService() {
        List<CandleResponse> candles = List.of(
                new CandleResponse(LocalDateTime.now(), null, null, null, new BigDecimal("65000"), null));
        when(cryptoQueryService.getCryptoHistory("bitcoin", CandlePeriod.THREE_MONTHS)).thenReturn(candles);

        List<?> result = service.getHistory(MarketType.CRYPTO, "bitcoin", CandlePeriod.THREE_MONTHS);

        assertThat(result).hasSize(1);
    }

    @Test
    void onMarketDataUpdatedWritesThroughToRedis() {
        List<MarketAssetResponse> gainers = List.of(asset("THYAO.IS", MarketType.STOCK, "5.0"));
        List<MarketAssetResponse> losers = List.of(asset("GARAN.IS", MarketType.STOCK, "-3.0"));
        when(stockProvider.getTopMovers(10, true)).thenReturn(gainers);
        when(stockProvider.getTopMovers(10, false)).thenReturn(losers);
        when(stockProvider.getAll()).thenReturn(List.of());

        service.onMarketDataUpdated(MarketType.STOCK);

        verify(topMoversRedisService).updateMovers(eq(MarketType.STOCK), anyList());
    }

    @Test
    void onMarketDataUpdatedIgnoresUnknownProviderType() {
        service.onMarketDataUpdated(MarketType.FOREX);

        verifyNoInteractions(topMoversRedisService);
    }

    @Test
    void searchWithFilterLosersFiltersPositiveChange() {
        when(stockProvider.search(isNull(), eq("changePercent"), eq("desc"), eq(0), eq(20)))
                .thenReturn(List.of(
                        asset("THYAO.IS", MarketType.STOCK, "5.0"),
                        asset("GARAN.IS", MarketType.STOCK, "-2.0")));

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "changePercent", "desc", "losers", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().code()).isEqualTo("GARAN.IS");
    }

    @Test
    void searchSortsByPriceAscending() {
        MarketAssetResponse cheap = new MarketAssetResponse("A", "A", null, MarketType.STOCK,
                new BigDecimal("10"), null, BigDecimal.ONE, LocalDateTime.now(), null);
        MarketAssetResponse expensive = new MarketAssetResponse("B", "B", null, MarketType.STOCK,
                new BigDecimal("500"), null, BigDecimal.ONE, LocalDateTime.now(), null);
        when(stockProvider.search(isNull(), eq("price"), eq("asc"), eq(0), eq(20)))
                .thenReturn(List.of(expensive, cheap));
        when(stockProvider.count()).thenReturn(2L);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), null, null, null, null,
                "price", "asc", "all", 0, 20);

        assertThat(result.content().getFirst().code()).isEqualTo("A");
        assertThat(result.content().getLast().code()).isEqualTo("B");
    }

    @Test
    void searchWithCodeReturnsEmptyWhenNotFound() {
        when(stockProvider.getByCode("NONEXISTENT")).thenReturn(null);

        PagedResponse<MarketAssetResponse> result = service.search(
                List.of(MarketType.STOCK), "NONEXISTENT", null, null, null,
                null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void getHistoryForexDelegatesToForexQueryService() {
        List<CandleResponse> candles = List.of(
                new CandleResponse(LocalDateTime.now(), null, null, null, new BigDecimal("38.5"), null));
        when(forexQueryService.getForexHistory("usd", CandlePeriod.ALL)).thenReturn(candles);

        List<?> result = service.getHistory(MarketType.FOREX, "usd", CandlePeriod.ALL);

        assertThat(result).hasSize(1);
        verify(forexQueryService).getForexHistory("usd", CandlePeriod.ALL);
    }

    @Test
    void getHistoryForexWithPeriodDelegatesToForexQueryService() {
        List<CandleResponse> candles = List.of(
                new CandleResponse(LocalDateTime.now(), null, null, null, new BigDecimal("38.5"), null));
        when(forexQueryService.getForexHistory("usd", CandlePeriod.ONE_MONTH)).thenReturn(candles);

        List<?> result = service.getHistory(MarketType.FOREX, "usd", CandlePeriod.ONE_MONTH);

        assertThat(result).hasSize(1);
        verify(forexQueryService).getForexHistory("usd", CandlePeriod.ONE_MONTH);
    }

    @Test
    void onMarketDataUpdatedSwallowsExceptionAndLogs() {
        when(stockProvider.getTopMovers(anyInt(), anyBoolean())).thenThrow(new RuntimeException("Redis down"));

        service.onMarketDataUpdated(MarketType.STOCK);

    }

    private MarketAssetResponse assetWithSegment(String code, MarketType type,
                                                  String changePercent, String segment) {
        return new MarketAssetResponse(code, code, null, type,
                new BigDecimal("100"), null,
                new BigDecimal(changePercent),
                LocalDateTime.now(),
                Map.of("stockSegment", segment));
    }
}
