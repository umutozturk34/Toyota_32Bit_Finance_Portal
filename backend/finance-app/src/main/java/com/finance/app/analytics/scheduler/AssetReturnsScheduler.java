package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.config.MarketDataInitializer;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Keeps the in-app asset-returns dataset warm. Startup warm-up first waits for the cold-start market-data
 * init to finish (so a fresh empty DB isn't queried before the base data exists); the daily precompute is
 * scheduled after the evening market-data refresh so the rebuilt dataset holds that day's fresh prices.
 * Mirrors {@link InflationBeaterScheduler}.
 */
@Log4j2
@Component
public class AssetReturnsScheduler {

    private final AssetReturnsService assetReturnsService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<MarketDataInitializer> marketDataInitializer;
    private final Executor taskExecutor;

    public AssetReturnsScheduler(AssetReturnsService assetReturnsService,
                                 TaskTrackingService taskTracker,
                                 ObjectProvider<MarketDataInitializer> marketDataInitializer,
                                 Executor taskExecutor) {
        this.assetReturnsService = assetReturnsService;
        this.taskTracker = taskTracker;
        this.marketDataInitializer = marketDataInitializer;
        this.taskExecutor = taskExecutor;
    }

    /** Warms the dataset the moment the cold-start market-data init finishes — never on a fixed wait window
     *  that could elapse mid-init and warm against half-loaded data. Mirrors {@link InflationBeaterScheduler}. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        MarketDataInitializer initializer = marketDataInitializer.getIfAvailable();
        if (initializer == null) {
            // Init is disabled (a populated DB is assumed) — warm right away, off the startup event thread.
            taskExecutor.execute(this::warmStartup);
            return;
        }
        // A failed/empty init still completes the future, which is fine: the gate is "init DONE", not "init OK".
        // A genuinely hung init leaves the daily precompute + on-demand warm to cover it.
        initializer.completion().whenCompleteAsync((v, ex) -> warmStartup(), taskExecutor);
    }

    private void warmStartup() {
        taskTracker.runTracked("returns-startup-warmup",
                "Asset returns cache warmup on startup",
                assetReturnsService::warmCache);
    }

    /** Daily precompute, scheduled after the evening market-data refresh (see {@code scheduler.yaml}). */
    @Scheduled(cron = "${app.scheduler.returns.daily-cron}", zone = "${app.timezone}")
    public void runDailyPrecompute() {
        taskTracker.runTracked("scheduled-returns-precompute",
                "Daily asset returns precompute",
                () -> {
                    assetReturnsService.clearCache();
                    assetReturnsService.warmCache();
                });
    }

}
