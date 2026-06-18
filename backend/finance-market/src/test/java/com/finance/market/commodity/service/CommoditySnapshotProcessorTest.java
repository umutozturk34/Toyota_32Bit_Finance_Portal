package com.finance.market.commodity.service;

import com.finance.common.config.AppProperties;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.commodity.client.YahooCommodityClient;
import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.commodity.mapper.CommodityMapper;
import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import com.finance.market.commodity.repository.CommodityCandleRepository;
import com.finance.market.commodity.repository.CommodityRepository;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.service.ExchangeRateProvider;
import com.finance.market.core.service.ExchangeRateSnapshot;
import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommoditySnapshotProcessorTest {

    @Mock private YahooCommodityClient yahooCommodityClient;
    @Mock private CommodityMapper commodityMapper;
    @Mock private CommodityRepository commodityRepository;
    @Mock private CommodityCandleRepository commodityCandleRepository;
    @SuppressWarnings("unchecked")
    @Mock private MarketCacheService<Commodity> commodityCacheService;
    @Mock private ExchangeRateProvider exchangeRateProvider;
    @Mock private PreciousMetalDerivativeCalculator derivativeCalculator;
    @Mock private YahooSymbolResolver yahooSymbolResolver;
    @Mock private CommoditySegmentResolver segmentResolver;
    @Mock private CommodityEntityWriter entityWriter;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;

    private CommoditySnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        CommodityProperties commodityProperties = new CommodityProperties();
        processor = new CommoditySnapshotProcessor(yahooCommodityClient, commodityMapper,
                commodityRepository, commodityCandleRepository, commodityCacheService,
                exchangeRateProvider, derivativeCalculator, yahooSymbolResolver, segmentResolver,
                entityWriter, transactionTemplate, trackedAssetQueryService,
                appProperties, commodityProperties);
    }

    private void stubTransactionTemplate() {
        doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private YahooCandleDto candle(int dayOfMonth, double close) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new YahooCandleDto(LocalDateTime.of(2026, 5, dayOfMonth, 0, 0),
                c, c, c, c, 100L);
    }

    private YahooQuoteDto quote(double price) {
        BigDecimal p = BigDecimal.valueOf(price);
        return new YahooQuoteDto(p, p, p, p, p, 100L, null, null);
    }

    private Map<String, BigDecimal> usdtryMap() {
        return Map.of("2026-05-11", new BigDecimal("32.4"), "2026-05-12", new BigDecimal("32.5"));
    }

    @Test
    void updateOne_returnsEarly_whenYahooSymbolUnresolved() {
        when(yahooSymbolResolver.resolve("UNKNOWN")).thenReturn(null);

        processor.updateOne("UNKNOWN", usdtryMap(), new ExchangeRateSnapshot(new BigDecimal("32"), null));

        verify(yahooCommodityClient, never()).fetchChartFull(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void updateOne_throws_whenQuoteIsNull() {
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("XAU"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of(candle(12, 2400))));

        assertThatThrownBy(() -> processor.updateOne("XAU", usdtryMap(),
                new ExchangeRateSnapshot(new BigDecimal("32.5"), null)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("No price");
    }

    @Test
    void updateOne_throws_whenUsdTryUnavailable() {
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("XAU"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(2400), List.of(candle(12, 2400))));

        assertThatThrownBy(() -> processor.updateOne("XAU", usdtryMap(),
                new ExchangeRateSnapshot(null, null)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("USDTRY rate not available");
    }

    @Test
    void updateOne_throws_whenCandlesEmpty() {
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("XAU"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(2400), List.of()));

        assertThatThrownBy(() -> processor.updateOne("XAU", usdtryMap(),
                new ExchangeRateSnapshot(new BigDecimal("32.5"), null)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("No candles");
    }

    @Test
    void updateOne_throws_whenTryAlignedCandlesEmpty() {
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("XAU"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(2400),
                        List.of(new YahooCandleDto(LocalDateTime.of(1990, 1, 1, 0, 0),
                                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L))));
        when(commodityRepository.findById("XAU")).thenReturn(Optional.empty());
        when(segmentResolver.resolve("XAU")).thenReturn(null);
        lenient().when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.COMMODITY))
                .thenReturn(Map.of());

        assertThatThrownBy(() -> processor.updateOne("XAU", usdtryMap(),
                new ExchangeRateSnapshot(new BigDecimal("32.5"), null)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("No USDTRY-aligned");
    }

    @Test
    void updateOne_persistsAndCachesExistingCommodity_andSkipsDerivatives_whenNotPreciousMetal() {
        Commodity existing = Commodity.builder().commodityCode("OIL").name("Petrol").build();
        YahooCandleDto today = candle(12, 80);
        YahooCandleDto yesterday = candle(11, 79);
        when(yahooSymbolResolver.resolve("OIL")).thenReturn("CL=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("OIL"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("CL=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(80), List.of(yesterday, today)));
        when(commodityRepository.findById("OIL")).thenReturn(Optional.of(existing));
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("2600"), new BigDecimal("2570"),
                new BigDecimal("80"), new BigDecimal("79"),
                null, null, null, 100L, null);
        when(commodityMapper.toSnapshotInput(any(), any(), any())).thenReturn(input);
        when(derivativeCalculator.hasDerivatives("OIL")).thenReturn(false);
        stubTransactionTemplate();

        processor.updateOne("OIL", usdtryMap(),
                new ExchangeRateSnapshot(new BigDecimal("32.5"), new BigDecimal("32.4")));

        verify(entityWriter).applySnapshot(eq(existing), eq(input), eq("CL=F"), anyInt());
        verify(entityWriter).upsertCandles(eq(existing), any(), anyInt());
        verify(entityWriter).refreshChangePercentFromCandles(eq(existing), anyInt());
        verify(commodityCacheService).putSnapshot("OIL", existing);
        verify(derivativeCalculator, never()).refreshDerivatives(any(), any(), any());
        verify(derivativeCalculator, never()).refreshDerivativeCandlesForSource(anyString());
    }

    @Test
    void updateOne_buildsNewCommodity_whenRepositoryEmpty() {
        YahooCandleDto today = candle(12, 2400);
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("XAU"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(2400), List.of(today)));
        when(commodityRepository.findById("XAU")).thenReturn(Optional.empty());
        when(segmentResolver.resolve("XAU")).thenReturn(null);
        when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.COMMODITY))
                .thenReturn(Map.of("XAU", "Altın"));
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("78000"), null, new BigDecimal("2400"), null, null, null, null, 100L, null);
        when(commodityMapper.toSnapshotInput(any(), any(), any())).thenReturn(input);
        when(derivativeCalculator.hasDerivatives("XAU")).thenReturn(true);
        stubTransactionTemplate();

        processor.updateOne("XAU", usdtryMap(),
                new ExchangeRateSnapshot(new BigDecimal("32.5"), new BigDecimal("32.4")));

        verify(commodityCacheService).putSnapshot(eq("XAU"), any(Commodity.class));
        verify(derivativeCalculator).refreshDerivatives(any(Commodity.class),
                eq(new BigDecimal("32.5")), eq(new BigDecimal("32.4")));
        verify(derivativeCalculator).refreshDerivativeCandlesForSource("XAU");
    }

    @Test
    void refreshOne_skipsRun_whenCodeIsBlank() {
        when(yahooSymbolResolver.normalize("   ")).thenReturn("");

        processor.refreshOne("   ");

        verify(yahooCommodityClient, never()).fetchChartFull(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void refreshOne_returnsEarly_whenSymbolResolveReturnsNull() {
        when(yahooSymbolResolver.normalize("UNKNOWN")).thenReturn("UNKNOWN");
        when(yahooSymbolResolver.resolve("UNKNOWN")).thenReturn(null);

        processor.refreshOne("UNKNOWN");

        verify(exchangeRateProvider, never()).getUsdTryHistory();
    }

    @Test
    void refreshOne_invokesUpdateFlow_whenSymbolResolves() {
        Commodity existing = Commodity.builder().commodityCode("OIL").name("Petrol").build();
        YahooCandleDto today = candle(12, 80);
        when(yahooSymbolResolver.normalize("oil")).thenReturn("OIL");
        when(yahooSymbolResolver.resolve("OIL")).thenReturn("CL=F");
        when(exchangeRateProvider.getUsdTryHistory()).thenReturn(usdtryMap());
        when(exchangeRateProvider.getCurrentUsdTry())
                .thenReturn(new ExchangeRateSnapshot(new BigDecimal("32.5"), new BigDecimal("32.4")));
        when(commodityCandleRepository.findFirstByCommodityCodeOrderByCandleDateDesc("OIL"))
                .thenReturn(Optional.empty());
        when(yahooCommodityClient.fetchChartFull(eq("CL=F"), anyString(), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(80), List.of(today)));
        when(commodityRepository.findById("OIL")).thenReturn(Optional.of(existing));
        CommoditySnapshotInput input = new CommoditySnapshotInput(
                new BigDecimal("2600"), null, new BigDecimal("80"), null, null, null, null, 100L, null);
        when(commodityMapper.toSnapshotInput(any(), any(), any())).thenReturn(input);
        when(derivativeCalculator.hasDerivatives("OIL")).thenReturn(false);
        stubTransactionTemplate();

        processor.refreshOne("oil");

        verify(commodityCacheService).putSnapshot("OIL", existing);
    }

    @Test
    void exists_returnsTrue_whenQuoteHasPrice() {
        when(yahooSymbolResolver.normalize("XAU")).thenReturn("XAU");
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), eq("1d"), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(quote(2400), List.of()));

        boolean result = processor.exists("XAU");

        assertThat(result).isTrue();
    }

    @Test
    void exists_returnsFalse_whenSymbolUnresolved() {
        when(yahooSymbolResolver.normalize("UNKNOWN")).thenReturn("UNKNOWN");
        when(yahooSymbolResolver.resolve("UNKNOWN")).thenReturn(null);

        boolean result = processor.exists("UNKNOWN");

        assertThat(result).isFalse();
    }

    @Test
    void exists_returnsFalse_whenQuoteIsNull() {
        when(yahooSymbolResolver.normalize("XAU")).thenReturn("XAU");
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), eq("1d"), anyString(), eq(true)))
                .thenReturn(new YahooChartFullResult<>(null, List.of()));

        boolean result = processor.exists("XAU");

        assertThat(result).isFalse();
    }

    @Test
    void exists_propagatesTemporarilyUnavailable_whenClientFailsTransiently() {
        when(yahooSymbolResolver.normalize("XAU")).thenReturn("XAU");
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(yahooCommodityClient.fetchChartFull(eq("GC=F"), eq("1d"), anyString(), eq(true)))
                .thenThrow(new RuntimeException("Yahoo 503"));

        // A transient Yahoo failure must NOT be reported as "does not exist"; it propagates so the caller retries.
        assertThatThrownBy(() -> processor.exists("XAU"))
                .isInstanceOf(com.finance.common.exception.BusinessException.class)
                .hasMessage("error.market.dataTemporarilyUnavailable");
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
