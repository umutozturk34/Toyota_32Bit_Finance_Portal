package com.finance.backend.scheduler;

import com.finance.backend.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stock Market Scheduler
 * Updates BIST stock data daily at 06:10 Istanbul time
 * Runs sequentially: snapshots first, then 5-year candles
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockScheduler {
    
    private final StockDataService stockDataService;
    
    /**
     * Daily scheduled update at 06:10 Istanbul time (before market opens)
     * Cron: 0 10 6 * * * (second minute hour day month weekday)
     * Zone: Europe/Istanbul
     */
    @Scheduled(cron = "0 10 6 * * *", zone = "Europe/Istanbul")
    public void scheduledStockUpdate() {
        log.info("Scheduled stock update triggered at 06:10 Istanbul time");
        
        try {
            // Update snapshots first (current prices)
            log.info("Step 1/2: Updating stock snapshots...");
            stockDataService.updateStockSnapshots();
            
            // Update 5-year candle data
            log.info("Step 2/2: Updating 5-year candle data...");
            stockDataService.updateStockCandles();
            
            log.info("Scheduled stock update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled stock update failed: {}", e.getMessage(), e);
        }
    }
}
