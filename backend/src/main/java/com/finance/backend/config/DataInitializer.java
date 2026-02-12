package com.finance.backend.config;

import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import com.finance.backend.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketDataService marketDataService;
    
    @Override
    public void run(String... args) {
        log.info("Running DataInitializer (Smart Mode)...");
        
        long cryptoCount = cryptoRepository.count();
        long candleCount = cryptoCandleRepository.count();
        
        if (cryptoCount > 0 && candleCount > 0) {
            log.info("Existing data found ({} cryptos, {} candles) - skipping initial fetch", cryptoCount, candleCount);
            log.info("Next update will run at scheduled time (06:30 Istanbul)");
            return;
        }
        
        log.info("No data found - starting initial 365-day fetch from CoinGecko API...");
        new Thread(() -> {
            try {
                marketDataService.fullMarketUpdate();
                log.info("Initial market data loaded successfully!");
            } catch (Exception e) {
                log.error("Initial market data fetch failed: {}", e.getMessage(), e);
            }
        }).start();
        
        log.info("DataInitializer completed (data fetching in background)");
    }
}

