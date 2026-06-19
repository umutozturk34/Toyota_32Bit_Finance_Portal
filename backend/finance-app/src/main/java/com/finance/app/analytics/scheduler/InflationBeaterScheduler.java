package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.config.MarketDataInitializer;
import com.finance.shared.service.TaskTrackingService;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Keeps the inflation-beater cache warm: precomputes EVERY supported period × EVERY selectable benchmark
 * indicator (enumerated from the catalog, not a config subset) so any user request hits a populated cache.
 * Startup warm-up waits for the cold-start market-data init to finish first (so a fresh empty DB isn't
 * flooded with external history calls before the base data exists); the daily precompute is scheduled after
 * the evening market-data refresh so the rebuilt cache holds fresh prices. Per-combination failures are
 * logged and skipped rather than aborting the batch.
 */
@Log4j2
@Component
public class InflationBeaterScheduler {

    private final InflationBeaterService inflationBeaterService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<MarketDataInitializer> marketDataInitializer;
    private final Executor taskExecutor;

    public InflationBeaterScheduler(InflationBeaterService inflationBeaterService,
                                    TaskTrackingService taskTracker,
                                    ObjectProvider<MarketDataInitializer> marketDataInitializer,
                                    Executor taskExecutor) {
        this.inflationBeaterService = inflationBeaterService;
        this.taskTracker = taskTracker;
        this.marketDataInitializer = marketDataInitializer;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Warms the cache once the application is up — but first waits for the cold-start market-data init
     * ({@link MarketDataInitializer}) to finish, so a fresh empty DB isn't flooded with the beater's external
     * history calls before the base data exists (and the warmed cache reflects real data, not empties). On a
     * populated DB that init future is already complete, so this proceeds immediately; when init is disabled
     * the bean is absent and we warm right away. {@code @Async} keeps the wait off the startup thread.
     */
    @WithSpan("beater.warmCacheOnStartup")
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        MarketDataInitializer initializer = marketDataInitializer.getIfAvailable();
        if (initializer == null) {
            // Init is disabled (a populated DB is assumed) — warm right away, off the startup event thread.
            taskExecutor.execute(this::warmStartup);
            return;
        }
        // Warm EXACTLY when the cold-start init chain finishes — however long it takes — instead of after a
        // fixed wait window that can elapse mid-init and warm against half-loaded data (e.g. before forex/USD
        // is in — the very bug this fixes). A failed/empty init still completes the future, which is fine: the
        // gate is "init DONE", not "init OK". whenCompleteAsync runs the warm on the task executor, never the
        // init thread, and a genuinely hung init simply leaves the daily precompute + on-demand warm to cover it.
        initializer.completion().whenCompleteAsync((v, ex) -> warmStartup(), taskExecutor);
    }

    private void warmStartup() {
        taskTracker.runTracked("beater-startup-warmup",
                "Beater cache warmup on startup",
                this::warmAll);
    }

    /**
     * Daily precompute, scheduled AFTER the evening market-data refresh (see {@code scheduler.yaml}) so the
     * rebuilt cache holds that day's freshly fetched prices/indicators rather than the previous day's.
     */
    @Scheduled(cron = "${app.scheduler.beater.daily-cron}", zone = "${app.timezone}")
    public void runDailyPrecompute() {
        taskTracker.runTracked("scheduled-beater-precompute",
                "Daily beater snapshot precompute",
                () -> {
                    inflationBeaterService.clearCache();
                    warmAll();
                });
    }


    /**
     * Refreshes every (period × benchmark) combination the UI can request — all supported periods against
     * every selectable benchmark indicator — so every parameter combination ends up cached. Both axes are
     * enumerated dynamically from the service so newly added indicators/periods warm automatically.
     */
    private void warmAll() {
        // Simulate the (~2700-asset) universe once per (period × currency) up front; the per-benchmark
        // rankings below then read from that cache instead of re-simulating the universe per benchmark.
        inflationBeaterService.warmUniverse();
        List<String> benchmarks = inflationBeaterService.warmableBenchmarkCodes();
        Set<String> periods = inflationBeaterService.warmablePeriods();
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
