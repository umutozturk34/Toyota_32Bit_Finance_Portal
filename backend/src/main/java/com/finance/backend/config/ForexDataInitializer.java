package com.finance.backend.config;
import com.finance.backend.model.Forex;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.Executor;
@Slf4j
@Component
@Order(3) 
@RequiredArgsConstructor
public class ForexDataInitializer implements CommandLineRunner {
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final Executor taskExecutor;
    @Override
    public void run(String... args) {
        log.info("Running ForexDataInitializer (Smart Mode)...");
        long forexCount = forexRepository.count();
        long candleCount = forexCandleRepository.count();
        if (forexCount > 0 && candleCount > 0) {
            List<Forex> missingPrice = forexRepository.findAll().stream()
                    .filter(f -> f.getCurrentPrice() == null)
                    .toList();
            long forexWithCandles = forexCandleRepository.findAll().stream()
                    .map(c -> c.getCurrencyCode())
                    .distinct()
                    .count();
            if (!missingPrice.isEmpty() || forexWithCandles < forexCount) {
                log.warn("Found {} currencies with missing prices, {}/{} with candle data - starting repair fetch",
                        missingPrice.size(), forexWithCandles, forexCount);
                taskExecutor.execute(() -> {
                    try {
                        log.info("[REPAIR] Fetching Yahoo Finance snapshots for all currencies...");
                        yahooForexService.syncAllYahooSnapshots();
                        log.info("[REPAIR] Fetching Yahoo Finance candles for all currencies...");
                        yahooForexService.syncAllYahooCandles();
                        log.info("[REPAIR] Forex data repair completed!");
                    } catch (Exception e) {
                        log.error("[REPAIR] Forex data repair failed: {}", e.getMessage(), e);
                    }
                });
                return;
            }
            log.info("Existing forex data found ({} currencies, {} candles) - skipping initial fetch", forexCount, candleCount);
            log.info("Next update will run at scheduled time (16:00 Istanbul)");
            return;
        }
        log.info("No forex data found - starting initial fetch from TCMB and Yahoo Finance API...");
        taskExecutor.execute(() -> {
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
        });
        log.info("ForexDataInitializer completed (data fetching in background)");
    }
}
