package com.finance.backend.config;

import com.finance.backend.repository.StockRepository;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2) 
@RequiredArgsConstructor
public class StockDataInitializer implements CommandLineRunner {
    
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockDataService stockDataService;
    
    @Override
    public void run(String... args) {
        log.info("Running StockDataInitializer (Smart Mode)...");
        long stockCount = stockRepository.count();
        long candleCount = stockCandleRepository.count();
        
        if (stockCount > 0 && candleCount > 0) {
            log.info("Existing stock data found ({} stocks, {} candles) - skipping initial fetch", stockCount, candleCount);
            log.info("Next update will run at scheduled time (06:10 Istanbul)");
            return;
        }
        
        log.info("No stock data found - starting initial 5-year fetch from Yahoo Finance API...");
        new Thread(() -> {
            try {
                log.info("Step 1/2: Fetching stock snapshots...");
                stockDataService.updateStockSnapshots();
            
                log.info("Step 2/2: Fetching 5-year candle data (this may take 10-15 minutes)...");
                stockDataService.updateStockCandles();
                
                log.info("Initial stock data loaded successfully!");
            } catch (Exception e) {
                log.error("Initial stock data fetch failed: {}", e.getMessage(), e);
            }
        }).start();
        
        log.info("StockDataInitializer completed (data fetching in background)");
    }
}
