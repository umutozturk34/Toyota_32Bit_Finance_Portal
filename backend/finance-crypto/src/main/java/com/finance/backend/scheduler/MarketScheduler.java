package com.finance.backend.scheduler;
import com.finance.backend.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScheduler {
    private final MarketDataService marketDataService;
    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Istanbul")
    public void runFullDailyMarketUpdate() {
        log.info("[MARKET-SCHEDULER] Starting full daily market synchronization...");
        long startTime = System.currentTimeMillis();
        try {
            log.info("[MARKET-SCHEDULER] Step 1/2: Updating crypto snapshots...");
            marketDataService.updateOnlySnapshots();
            log.info("[MARKET-SCHEDULER] Step 2/2: Updating candle history and self-healing...");
            marketDataService.updateOnlyCandles();
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("[MARKET-SCHEDULER] Full daily update completed successfully in {} seconds.", duration);
        } catch (Exception e) {
            log.error("[MARKET-SCHEDULER-FATAL] Daily update failed! Error: {}", e.getMessage(), e);
        }
    }
}