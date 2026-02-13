package com.finance.backend.config;

import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(3) 
@RequiredArgsConstructor
public class ForexDataInitializer implements CommandLineRunner {
    
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    
    @Override
    public void run(String... args) {
        log.info("Running ForexDataInitializer (Smart Mode)...");
        long forexCount = forexRepository.count();
        long candleCount = forexCandleRepository.count();
        
        if (forexCount > 0 && candleCount > 0) {
            log.info("Existing forex data found ({} currencies, {} candles) - skipping initial fetch", forexCount, candleCount);
            log.info("Next update will run at scheduled time (16:00 Istanbul)");
            return;
        }
        
        log.info("No forex data found - starting initial fetch from TCMB and Yahoo Finance API...");
        new Thread(() -> {
            try {
                log.info("Step 1/3: Fetching TCMB official rates (21 currencies)...");
                tcmbForexService.fetchAndSaveTcmbRates();
                
                log.info("Step 2/3: Fetching Yahoo Finance snapshots...");
                yahooForexService.syncAllYahooSnapshots();
                
                log.info("Step 3/3: Fetching Yahoo Finance 5-year candle data (this may take 5-10 minutes)...");
                yahooForexService.syncAllYahooCandles();
                
                log.info("Initial forex data loaded successfully!");
            } catch (Exception e) {
                log.error("Initial forex data fetch failed: {}", e.getMessage(), e);
            }
        }).start();
        
        log.info("ForexDataInitializer completed (data fetching in background)");
    }
}
