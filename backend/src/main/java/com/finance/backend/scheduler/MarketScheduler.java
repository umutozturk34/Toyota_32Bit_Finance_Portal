package com.finance.backend.scheduler;

import com.finance.backend.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class MarketScheduler {
    
    private final MarketDataService marketDataService;
    @Scheduled(cron = "0 30 18 * * *", zone = "Europe/Istanbul")
    public void scheduledMarketUpdate() {
        log.info("Scheduled market update triggered at 18:30 Istanbul time");
        
        try {
            marketDataService.fullMarketUpdate();
            log.info("Scheduled market update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled market update failed: {}", e.getMessage(), e);
        }
    }   
}
