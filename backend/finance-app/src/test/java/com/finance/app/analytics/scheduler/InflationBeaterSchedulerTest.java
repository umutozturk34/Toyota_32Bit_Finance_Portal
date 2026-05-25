package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InflationBeaterSchedulerTest {

    @Mock
    private InflationBeaterService inflationBeaterService;

    @Mock
    private TaskTrackingService taskTracker;

    private InflationBeaterScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InflationBeaterScheduler(
                inflationBeaterService,
                taskTracker,
                List.of("1M", "1Y"),
                List.of("TP.TUFE1YI.T1", "TP.POLICY"));
        doAnswer(inv -> {
            Runnable r = inv.getArgument(2);
            r.run();
            return null;
        }).when(taskTracker).runTracked(any(), any(), any());
    }

    @Test
    void shouldDelegateStartupWarmup_whenApplicationReady() {
        scheduler.warmCacheOnStartup();

        verify(taskTracker).runTracked(eq("beater-startup-warmup"), any(), any());
        verify(inflationBeaterService, times(4)).refresh(any(), any());
        verify(inflationBeaterService, never()).clearCache();
    }

    @Test
    void shouldClearCacheAndWarm_whenDailyCronFires() {
        scheduler.runDailyPrecompute();

        var order = inOrder(inflationBeaterService);
        order.verify(inflationBeaterService).clearCache();
        order.verify(inflationBeaterService, times(4)).refresh(any(), any());
        verify(taskTracker).runTracked(eq("scheduled-beater-precompute"), any(), any());
    }

    @Test
    void shouldInvokeRefreshForEveryPeriodBenchmarkCombination_whenWarmingAll() {
        scheduler.warmCacheOnStartup();

        verify(inflationBeaterService).refresh("1M", "TP.TUFE1YI.T1");
        verify(inflationBeaterService).refresh("1Y", "TP.TUFE1YI.T1");
        verify(inflationBeaterService).refresh("1M", "TP.POLICY");
        verify(inflationBeaterService).refresh("1Y", "TP.POLICY");
    }

    @Test
    void shouldSwallowIndividualFailures_whenSingleRefreshThrows() {
        doThrow(new RuntimeException("network down"))
                .when(inflationBeaterService).refresh("1M", "TP.POLICY");

        scheduler.warmCacheOnStartup();

        verify(inflationBeaterService, times(4)).refresh(any(), any());
    }

    @Test
    void shouldHandleEmptyConfiguration_whenNoPeriodsOrBenchmarks() {
        InflationBeaterScheduler empty = new InflationBeaterScheduler(
                inflationBeaterService, taskTracker, List.of(), List.of());

        empty.warmCacheOnStartup();

        verify(inflationBeaterService, never()).refresh(any(), any());
        verify(taskTracker).runTracked(eq("beater-startup-warmup"), any(), any());
    }
}
