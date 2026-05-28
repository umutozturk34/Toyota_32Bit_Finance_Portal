package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the inflation-beater cache warm: precomputes every configured period × benchmark combination on
 * startup and again on a daily cron (clearing first), so user requests hit a populated cache. Failures
 * for individual combinations are logged and skipped rather than aborting the batch.
 */
@Log4j2
@Component
public class InflationBeaterScheduler {

    private final InflationBeaterService inflationBeaterService;
    private final TaskTrackingService taskTracker;
    private final List<String> periods;
    private final List<String> benchmarks;

    public InflationBeaterScheduler(InflationBeaterService inflationBeaterService,
                                    TaskTrackingService taskTracker,
                                    @Value("${app.scheduler.beater.periods}") List<String> periods,
                                    @Value("${app.scheduler.beater.benchmarks}") List<String> benchmarks) {
        this.inflationBeaterService = inflationBeaterService;
        this.taskTracker = taskTracker;
        this.periods = periods;
        this.benchmarks = benchmarks;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        taskTracker.runTracked("beater-startup-warmup",
                "Beater cache warmup on startup",
                this::warmAll);
    }

    @Scheduled(cron = "${app.scheduler.beater.daily-cron}", zone = "${app.timezone}")
    public void runDailyPrecompute() {
        taskTracker.runTracked("scheduled-beater-precompute",
                "Daily beater snapshot precompute",
                () -> {
                    inflationBeaterService.clearCache();
                    warmAll();
                });
    }

    private void warmAll() {
        int total = periods.size() * benchmarks.size();
        int done = 0;
        int failed = 0;
        for (String benchmark : benchmarks) {
            for (String period : periods) {
                try {
                    inflationBeaterService.refresh(period, benchmark);
                    done++;
                } catch (RuntimeException ex) {
                    log.warn("Beater warm failed period={} benchmark={}: {}",
                            period, benchmark, ex.getMessage());
                    failed++;
                }
            }
        }
        log.info("Beater warm finished total={} done={} failed={}", total, done, failed);
    }
}
