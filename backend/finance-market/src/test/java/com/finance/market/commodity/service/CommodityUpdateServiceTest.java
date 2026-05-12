package com.finance.market.commodity.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.commodity.config.CommodityProperties;
import com.finance.market.core.service.ExchangeRateProvider;
import com.finance.market.core.service.ExchangeRateSnapshot;
import com.finance.market.core.service.TrackedAssetQueryService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommodityUpdateServiceTest {

    @Mock private CommoditySnapshotProcessor snapshotProcessor;
    @Mock private ExchangeRateProvider exchangeRateProvider;
    @Mock private PreciousMetalDerivativeCalculator derivativeCalculator;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private YahooSymbolResolver yahooSymbolResolver;

    private CommodityUpdateService service;

    @BeforeEach
    void setUp() {
        CommodityProperties props = new CommodityProperties();
        service = new CommodityUpdateService(snapshotProcessor, exchangeRateProvider,
                derivativeCalculator, trackedAssetQueryService, yahooSymbolResolver, props);
    }

    @Test
    void getMarketType_returnsCommodity() {
        assertThat(service.getMarketType()).isEqualTo(MarketType.COMMODITY);
    }

    @Test
    void refreshAll_skipsSync_whenNoTrackedCommodities() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY)).thenReturn(List.of());

        service.refreshAll();

        verify(snapshotProcessor, never()).updateOne(anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshAll_raises_whenAllCommoditiesLackYahooSymbol() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY)).thenReturn(List.of("XAU"));
        when(yahooSymbolResolver.resolve("XAU")).thenReturn(null);

        assertThatThrownBy(() -> service.refreshAll())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Yahoo symbol");
    }

    @Test
    void refreshAll_raises_whenUsdTryRatesEmpty() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY)).thenReturn(List.of("XAU"));
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(exchangeRateProvider.getUsdTryHistory()).thenReturn(Map.of());

        assertThatThrownBy(() -> service.refreshAll())
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("USDTRY");
    }

    @Test
    void refreshAll_invokesProcessorForEachCommodity_whenRatesAvailable() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY))
                .thenReturn(List.of("XAU", "OIL"));
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(yahooSymbolResolver.resolve("OIL")).thenReturn("CL=F");
        Map<String, BigDecimal> rates = Map.of("2026-05-12", new BigDecimal("32.5"));
        when(exchangeRateProvider.getUsdTryHistory()).thenReturn(rates);
        ExchangeRateSnapshot snap = new ExchangeRateSnapshot(new BigDecimal("32.5"), new BigDecimal("32.4"));
        when(exchangeRateProvider.getCurrentUsdTry()).thenReturn(snap);

        service.refreshAll();

        verify(snapshotProcessor).updateOne("XAU", rates, snap);
        verify(snapshotProcessor).updateOne("OIL", rates, snap);
    }

    @Test
    void refreshAll_skipsCommoditiesWithoutYahooSymbol_butProceedsWithRest() {
        when(trackedAssetQueryService.getCodes(TrackedAssetType.COMMODITY))
                .thenReturn(List.of("XAU", "MISSING"));
        when(yahooSymbolResolver.resolve("XAU")).thenReturn("GC=F");
        when(yahooSymbolResolver.resolve("MISSING")).thenReturn(null);
        Map<String, BigDecimal> rates = Map.of("2026-05-12", new BigDecimal("32.5"));
        when(exchangeRateProvider.getUsdTryHistory()).thenReturn(rates);
        ExchangeRateSnapshot snap = new ExchangeRateSnapshot(new BigDecimal("32.5"), new BigDecimal("32.4"));
        when(exchangeRateProvider.getCurrentUsdTry()).thenReturn(snap);

        service.refreshAll();

        verify(snapshotProcessor).updateOne("XAU", rates, snap);
        verify(snapshotProcessor, never()).updateOne(org.mockito.ArgumentMatchers.eq("MISSING"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refresh_delegatesToProcessor() {
        service.refresh("XAU");

        verify(snapshotProcessor).refreshOne("XAU");
    }

    @Test
    void exists_delegatesToProcessor() {
        when(snapshotProcessor.exists("XAU")).thenReturn(true);

        assertThat(service.exists("XAU")).isTrue();
    }
}
