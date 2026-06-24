package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.config.MarketDataInitializer;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InflationBeaterSchedulerTest {

    @Mock
    private InflationBeaterService inflationBeaterService;

    @Mock
    private TaskTrackingService taskTracker;

    @Mock
    private ObjectProvider<MarketDataInitializer> marketDataInitializer;

    private InflationBeaterScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Runnable::run = a synchronous executor: the init-completion callback and the no-initializer path
        // both run the warm inline, so the assertions below see it happen within warmCacheOnStartup().
        scheduler = new InflationBeaterScheduler(inflationBeaterService, taskTracker, marketDataInitializer, Runnable::run);
        // Default: no cold-start init bean (so the warm-up proceeds immediately) and a small two-by-two
        // coverage matrix. Lenient because not every test exercises every stub.
        lenient().when(marketDataInitializer.getIfAvailable()).thenReturn(null);
        lenient().when(inflationBeaterService.warmablePeriods())
                .thenReturn(new LinkedHashSet<>(List.of("1M", "1Y")));
        lenient().when(inflationBeaterService.warmableBenchmarkCodes())
                .thenReturn(List.of("TP.TUFE1YI.T1", "TP.POLICY"));
        doAnswer(inv -> {
            Runnable r = inv.getArgument(2);
            r.run();
            return null;
        }).when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void shouldDelegateStartupWarmup_whenApplicationReady() {
        // Act
        scheduler.warmCacheOnStartup();

        // Assert
        verify(taskTracker).runTracked(eq("beater-startup-warmup"), any(), any());
        verify(inflationBeaterService, times(4)).refresh(any(), any());
        verify(inflationBeaterService, never()).clearCache();
    }

    @Test
    void shouldClearCacheAndWarm_whenDailyCronFires() {
        // Act
        scheduler.runDailyPrecompute();

        // Assert
        var order = inOrder(inflationBeaterService);
        order.verify(inflationBeaterService).clearCache();
        order.verify(inflationBeaterService, times(4)).refresh(any(), any());
        verify(taskTracker).runTracked(eq("scheduled-beater-precompute"), any(), any());
    }

    @Test
    void shouldInvokeRefreshForEveryPeriodBenchmarkCombination_whenWarmingAll() {
        // Act
        scheduler.warmCacheOnStartup();

        // Assert
        verify(inflationBeaterService).refresh("1M", "TP.TUFE1YI.T1");
        verify(inflationBeaterService).refresh("1Y", "TP.TUFE1YI.T1");
        verify(inflationBeaterService).refresh("1M", "TP.POLICY");
        verify(inflationBeaterService).refresh("1Y", "TP.POLICY");
    }

    @Test
    void shouldSwallowIndividualFailures_whenSingleRefreshThrows() {
        // Arrange
        doThrow(new RuntimeException("network down"))
                .when(inflationBeaterService).refresh("1M", "TP.POLICY");

        // Act
        scheduler.warmCacheOnStartup();

        // Assert
        verify(inflationBeaterService, times(4)).refresh(any(), any());
    }

    @Test
    void shouldWarmNothing_whenNoBenchmarksEnumerated() {
        // Arrange
        when(inflationBeaterService.warmableBenchmarkCodes()).thenReturn(List.of());

        // Act
        scheduler.warmCacheOnStartup();

        // Assert
        verify(inflationBeaterService, never()).refresh(any(), any());
        verify(taskTracker).runTracked(eq("beater-startup-warmup"), any(), any());
    }

    @Test
    void shouldWarmWhenInitCompletes_whenInitializerPresent() {
        // Arrange — a present cold-start initializer whose completion future is already done.
        MarketDataInitializer initializer = mock(MarketDataInitializer.class);
        when(initializer.completion()).thenReturn(CompletableFuture.completedFuture(null));
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        scheduler.warmCacheOnStartup();

        // Assert — the warm-up gated on the init-completion callback, then warmed every combination.
        verify(initializer).completion();
        verify(inflationBeaterService, times(4)).refresh(any(), any());
    }

    @Test
    void shouldWarmAnyway_whenMarketDataInitFails() {
        // Arrange — a present initializer whose completion future fails; whenComplete still fires the warm.
        MarketDataInitializer initializer = mock(MarketDataInitializer.class);
        when(initializer.completion())
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("init boom")));
        when(marketDataInitializer.getIfAvailable()).thenReturn(initializer);

        // Act
        scheduler.warmCacheOnStartup();

        // Assert — a failed/stalled init must not strand the cache; it warms anyway.
        verify(inflationBeaterService, times(4)).refresh(any(), any());
    }
}
