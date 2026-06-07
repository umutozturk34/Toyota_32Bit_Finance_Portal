package com.finance.app.analytics.scheduler;

import com.finance.app.analytics.service.AssetReturnsService;
import com.finance.app.config.MarketDataInitializer;
import com.finance.shared.service.TaskTrackingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Keeps the in-app asset-returns dataset warm. Startup warm-up first waits for the cold-start market-data
 * init to finish (so a fresh empty DB isn't queried before the base data exists); the daily precompute is
 * scheduled after the evening market-data refresh so the rebuilt dataset holds that day's fresh prices.
 * Mirrors {@link InflationBeaterScheduler}.
 */
@Log4j2
@Component
public class AssetReturnsScheduler {

    /** Upper bound on waiting for cold-start init before warming anyway; a hung fetch must never block forever. */
    private static final long INIT_WAIT_MINUTES = 30;

    private final AssetReturnsService assetReturnsService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<MarketDataInitializer> marketDataInitializer;

    public AssetReturnsScheduler(AssetReturnsService assetReturnsService,
                                 TaskTrackingService taskTracker,
                                 ObjectProvider<MarketDataInitializer> marketDataInitializer) {
        this.assetReturnsService = assetReturnsService;
        this.taskTracker = taskTracker;
        this.marketDataInitializer = marketDataInitializer;
    }

    /** Warms the dataset once the app is up, after waiting for the cold-start market-data init to finish. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        awaitMarketDataInit();
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

    /** Blocks (off the startup thread, via {@code @Async}) until the cold-start data load finishes; warms
     *  anyway on timeout/interrupt so a stalled fetch can't strand the dataset permanently empty. */
    private void awaitMarketDataInit() {
        MarketDataInitializer initializer = marketDataInitializer.getIfAvailable();
        if (initializer == null) {
            return;
        }
        try {
            initializer.completion().get(INIT_WAIT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while awaiting market-data init; warming asset returns anyway");
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Market-data init did not finish in time; warming asset returns anyway: {}", e.getMessage());
        }
    }
}
