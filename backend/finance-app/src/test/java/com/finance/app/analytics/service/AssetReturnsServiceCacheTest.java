package com.finance.app.analytics.service;

import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.response.AssetReturnsResponse;
import com.finance.app.config.MarketDataInitializer;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.core.service.TrackedAssetQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetReturnsServiceCacheTest {

    @Mock private UnifiedHistoryService historyService;
    @Mock private TrackedAssetQueryService trackedAssetQueryService;
    @Mock private CurrencyConverter currencyConverter;
    @Spy private AssetReturnsCacheManager cacheManager = new AssetReturnsCacheManager();

    @InjectMocks
    private AssetReturnsService service;

    @BeforeEach
    void wireTrackedDefaults() {
        for (TrackedAssetType t : TrackedAssetType.values()) {
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
        // lenient: shouldClearCache_unconditionally wires the initializer but clearCache() is not
        // readiness-gated, so it never reads completion() — strict stubbing would flag it as unnecessary.
        lenient().when(initializer.completion()).thenReturn(future);
        ReflectionTestUtils.setField(service, "marketDataInitializer", initializer);
    }

    @Test
    void shouldReturnNullFromPeek_whenMarketDataStillInitializing() {
        // Arrange
        wireInitializer(false);

        // Act
        AssetReturnsResponse peek = service.peekReturns();

        // Assert
        assertThat(peek).isNull();
        verify(cacheManager, never()).peek();
    }

    @Test
    void shouldPeekCache_whenDataReady() {
        // Arrange — ready init plus a cold cache: peek delegates and returns the manager's null.
        wireInitializer(true);

        // Act
        AssetReturnsResponse peek = service.peekReturns();

        // Assert
        assertThat(peek).isNull();
        verify(cacheManager).peek();
    }

    @Test
    void shouldSkipWarm_whenMarketDataStillInitializing() {
        // Arrange
        wireInitializer(false);

        // Act
        service.warmCache();

        // Assert — no compute path touched while cold.
        verify(cacheManager, never()).refresh(any());
        verify(historyService, never()).getSeries(any(), any(), any());
    }

    @Test
    void shouldRefreshCache_whenWarmingWithDataReady() {
        // Arrange
        wireInitializer(true);

        // Act
        service.warmCache();

        // Assert
        verify(cacheManager).refresh(any());
    }

    @Test
    void shouldDelegateWarmAsyncToWarmCache() {
        // Arrange
        wireInitializer(true);

        // Act
        service.warmAsync();

        // Assert
        verify(cacheManager).refresh(any());
    }

    @Test
    void shouldClearCache_unconditionally() {
        // Arrange — clearCache is not gated by readiness, so no initializer wiring is needed.

        // Act
        service.clearCache();

        // Assert
        verify(cacheManager).clear();
    }

    @Test
    void shouldSkipAsset_whenSeriesFetchThrows() {
        // Arrange — one stock whose history fetch throws is logged and treated as an empty series, so the
        // asset produces no row rather than failing the whole dataset.
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("BOOM"));
        when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.STOCK)).thenReturn(Map.of("BOOM", "Boom"));
        when(historyService.getSeries(any(), any(), any())).thenThrow(new RuntimeException("fetch failed"));

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert
        assertThat(response.assets()).isEmpty();
    }

    @Test
    void shouldOmitFxLeg_whenFxConversionThrows() {
        // Arrange — a stock with TRY history doubles, but USD/EUR conversion throws: TRY ranking still computes,
        // the FX legs degrade to null instead of failing.
        LocalDate today = LocalDate.now();
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK)).thenReturn(List.of("AAA"));
        when(trackedAssetQueryService.getDisplayNameMap(TrackedAssetType.STOCK)).thenReturn(Map.of("AAA", "AAA"));
        when(historyService.getSeries(any(), any(), any())).thenReturn(List.of(
                new HistoryPoint(today.minusYears(1).plusDays(2), new BigDecimal("100")),
                new HistoryPoint(today.minusDays(2), new BigDecimal("200"))));
        when(currencyConverter.convertSeries(any(), any(), any())).thenThrow(new RuntimeException("no fx"));

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert
        assertThat(response.assets()).hasSize(1);
        var pr = response.assets().get(0).periods().get("1Y");
        assertThat(pr.returnPct()).isEqualByComparingTo("100.00");
        assertThat(pr.usd()).isNull();
        assertThat(pr.eur()).isNull();
    }

    @Test
    void shouldSkipTypeAndKeepGoing_whenEnumerationThrows() {
        // Arrange — enumerating one tracked type throws; the universe build logs and continues with the rest.
        when(trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK))
                .thenThrow(new RuntimeException("enumerate failed"));

        // Act
        AssetReturnsResponse response = service.getReturns();

        // Assert — no exception escapes; dataset is simply empty (other types have no codes).
        assertThat(response.assets()).isEmpty();
    }
}
