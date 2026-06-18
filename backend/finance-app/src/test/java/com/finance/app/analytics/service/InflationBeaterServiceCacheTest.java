package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.config.MarketDataInitializer;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.macro.service.MacroIndicatorQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InflationBeaterServiceCacheTest {

    private static final String DEFAULT_BENCHMARK = "TP.GENENDEKS.T1";

    @Mock private ScenarioService scenarioService;
    @Mock private UnifiedHistoryService historyService;
    @Mock private MacroIndicatorQueryService macroQueryService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Spy private BeaterCacheManager cacheManager = new BeaterCacheManager();
    @Mock private ObjectProvider<com.finance.market.core.service.AssetNativeCurrencyResolver> nativeCurrencyResolver;
    @Mock private ObjectProvider<MarketDataInitializer> marketDataInitializer;

    private InflationBeaterService service;

    @BeforeEach
    void wireTrackedDefaults() {
        // Built by hand rather than @InjectMocks: both cold-start guards are erased-type ObjectProvider, which
        // constructor injection can't disambiguate. getIfAvailable() defaults to null (ready) until stubbed.
        // The universe builder/simulator collaborators are wired from the same catalog/scenario mocks.
        InflationBeaterUniverseBuilder universeBuilder =
                new InflationBeaterUniverseBuilder(macroQueryService, trackedAssetQueryService);
        InflationBeaterUniverseSimulator universeSimulator =
                new InflationBeaterUniverseSimulator(scenarioService);
        service = new InflationBeaterService(scenarioService, historyService, macroQueryService,
                cacheManager, universeBuilder, universeSimulator, nativeCurrencyResolver, marketDataInitializer);
        for (TrackedAssetType t : TrackedAssetType.values()) {
            lenient().when(trackedAssetQueryService.getCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getEnabledCodes(t)).thenReturn(List.of());
            lenient().when(trackedAssetQueryService.getDisplayNameMap(t)).thenReturn(Map.of());
        }
    }

    private void wireInitializer(boolean done) {
        MarketDataInitializer initializer = org.mockito.Mockito.mock(MarketDataInitializer.class);
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (done) {
            future.complete(null);
        }
        when(initializer.completion()).thenReturn(future);
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ZZ", "13M", "" })
    void shouldReturnNullFromPeekCache_whenPeriodTokenUnknown(String period) {
        // Act
        InflationBeaterResponse peek = service.peekCache(period, null);

        // Assert
        assertThat(peek).isNull();
        verify(cacheManager, never()).peek(any());
    }

    @Test
    void shouldPeekCacheWithDefaultBenchmarkKey_whenBenchmarkBlank() {
        // Arrange — a blank benchmark resolves to the default index; the peek key carries the AUTO framing.
        String expectedKey = "1Y|" + DEFAULT_BENCHMARK + "|AUTO";

        // Act
        InflationBeaterResponse peek = service.peekCache("1Y", " ");

        // Assert — cold cache returns null but the key was built from the resolved benchmark.
        assertThat(peek).isNull();
        verify(cacheManager).peek(expectedKey);
    }

    @Test
    void shouldSkipWarmAsync_whenMarketDataStillInitializing() {
        // Arrange
        wireInitializer(false);

        // Act
        service.warmAsync("1Y", null);

        // Assert
        verify(cacheManager, never()).warmAsync(any(), any(), any(), any());
    }

    @Test
    void shouldSkipWarmAsync_whenPeriodTokenUnknown() {
        // Arrange — ready init, but the token is invalid so the warm short-circuits before touching the cache.
        wireInitializer(true);

        // Act
        service.warmAsync("ZZ", null);

        // Assert
        verify(cacheManager, never()).warmAsync(any(), any(), any(), any());
    }

    @Test
    void shouldDelegateWarmAsyncToCache_whenReadyAndPeriodValid() {
        // Arrange — stub the cache warm so the supplier (compute) is never invoked; assert correct key/args.
        wireInitializer(true);
        doNothing().when(cacheManager).warmAsync(any(), any(), any(), any());

        // Act
        service.warmAsync("6M", null);

        // Assert
        verify(cacheManager).warmAsync(eq("6M|" + DEFAULT_BENCHMARK + "|AUTO"), eq("6M"), eq(DEFAULT_BENCHMARK), any());
    }

    @Test
    void shouldSkipRefresh_whenPeriodTokenUnknown() {
        // Act
        service.refresh("nope", null);

        // Assert
        verify(cacheManager, never()).refresh(any(), any(), any(), any());
    }

    @Test
    void shouldDelegateRefreshToCache_whenPeriodValid() {
        // Arrange — stub the cache refresh so compute isn't run; assert correct key/args.
        doNothing().when(cacheManager).refresh(any(), any(), any(), any());

        // Act
        service.refresh("3M", "TP.CUSTOM");

        // Assert
        verify(cacheManager).refresh(eq("3M|TP.CUSTOM|AUTO"), eq("3M"), eq("TP.CUSTOM"), any());
    }

    @Test
    void shouldClearCache() {
        // Act
        service.clearCache();

        // Assert
        verify(cacheManager).clear();
    }
}
