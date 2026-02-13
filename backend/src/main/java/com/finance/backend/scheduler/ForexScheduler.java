package com.finance.backend.scheduler;

import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ForexScheduler {
    
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    
    @Scheduled(cron = "0 0 16 * * *", zone = "Europe/Istanbul")
    public void scheduledForexUpdate() {
        log.info("Scheduled forex update triggered at 16:00 Istanbul time");
        
        try {
            log.info("Step 1/3: Updating TCMB forex rates...");
            tcmbForexService.fetchAndSaveTcmbRates();
            
            log.info("Step 2/3: Updating Yahoo Finance snapshots...");
            yahooForexService.syncAllYahooSnapshots();
            
            log.info("Step 3/3: Updating Yahoo Finance candles (5 years)...");
            yahooForexService.syncAllYahooCandles();
            
            log.info("Scheduled forex update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled forex update failed: {}", e.getMessage(), e);
        }
    }
}
