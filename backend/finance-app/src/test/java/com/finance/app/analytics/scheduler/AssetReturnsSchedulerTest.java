package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.config.MarketDataInitializer;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetReturnsSchedulerTest {

    @Mock
    private AssetReturnsService assetReturnsService;

    @Mock
    private TaskTrackingService taskTracker;

    @Mock
    private ObjectProvider<MarketDataInitializer> marketDataInitializer;

    private AssetReturnsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AssetReturnsScheduler(assetReturnsService, taskTracker, marketDataInitializer);
        // Default: no cold-start init bean (warm-up proceeds immediately). Lenient — not every test uses it.
        lenient().when(marketDataInitializer.getIfAvailable()).thenReturn(null);
        doAnswer(inv -> {
            Runnable r = inv.getArgument(2);
            r.run();
            return null;
        }).when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void shouldWarmCache_whenApplicationReady() {
        // Act
        scheduler.warmCacheOnStartup();

        // Assert
        verify(taskTracker).runTracked(eq("returns-startup-warmup"), any(), any());
        verify(assetReturnsService).warmCache();
        verify(assetReturnsService, never()).clearCache();
    }

    @Test
    void shouldClearThenWarm_whenDailyCronFires() {
        // Act
        scheduler.runDailyPrecompute();

        // Assert
        var order = inOrder(assetReturnsService);
        order.verify(assetReturnsService).clearCache();
        order.verify(assetReturnsService).warmCache();
        verify(taskTracker).runTracked(eq("scheduled-returns-precompute"), any(), any());
    }

    @Test
    void shouldAwaitMarketDataInit_beforeWarming_whenInitializerPresent() {
        // Arrange — a present cold-start initializer whose completion future is already done.
        MarketDataInitializer initializer = mock(MarketDataInitializer.class);
        when(initializer.completion()).thenReturn(CompletableFuture.completedFuture(null));
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        scheduler.warmCacheOnStartup();

        // Assert — the warm-up waited on init completion, then warmed.
        verify(initializer).completion();
        verify(assetReturnsService).warmCache();
    }

    @Test
    void shouldWarmAnyway_whenMarketDataInitFails() {
        // Arrange — a present initializer whose completion future fails (get() throws ExecutionException).
        MarketDataInitializer initializer = mock(MarketDataInitializer.class);
        when(initializer.completion())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("init boom")));
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        scheduler.warmCacheOnStartup();

        // Assert — a failed/stalled init must not strand the dataset; it warms anyway.
        verify(assetReturnsService).warmCache();
    }
}
