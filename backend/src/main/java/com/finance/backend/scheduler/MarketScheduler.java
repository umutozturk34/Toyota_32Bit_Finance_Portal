package com.finance.backend.scheduler;

import com.finance.backend.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Market Data Scheduler - Automatic Pilot
 * Runs market data updates on a fixed schedule
 * 
 * Schedule: Every day at 18:30 Istanbul time
 * Operation: Full market update (snapshots + candles)
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MarketScheduler {
    
    private final MarketDataService marketDataService;
    
    /**
     * Scheduled market update - Runs daily at 06:30 Istanbul time
     * Cron format: "second minute hour day month day-of-week"
     * 0 30 6 * * * = 06:30 every day
     * 
     * Smart Update Logic:
     * - First run: Fetches 365 days of historical data
     * - Daily runs: Fetches only last 1 day + prunes old data
     */
    @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Istanbul")
    public void scheduledMarketUpdate() {
        log.info("⏰ Scheduled market update triggered at 06:30 Istanbul time");
        
        try {
            marketDataService.fullMarketUpdate();
            log.info("✅ Scheduled market update completed successfully");
        } catch (Exception e) {
            log.error("❌ Scheduled market update failed: {}", e.getMessage(), e);
        }
    }
}
