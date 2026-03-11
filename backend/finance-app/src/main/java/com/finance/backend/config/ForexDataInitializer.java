package com.finance.backend.config;
import com.finance.backend.model.Forex;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.Executor;
@Log4j2
@Component
@Order(3) 
@RequiredArgsConstructor
public class ForexDataInitializer implements CommandLineRunner {
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
    @Override
    public void run(String... args) {
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
                log.warn("Forex repair needed: {} missing prices, {}/{} with candles",
                        missingPrice.size(), forexWithCandles, forexCount);
                TaskInfo started = taskTracker.startTask("init-forex-repair", "Forex data repair (Yahoo snapshots + candles)");
                taskExecutor.execute(() -> {
                    try {
                        yahooForexService.syncAllYahooSnapshots();
                        yahooForexService.syncAllYahooCandles();
                        taskTracker.completeTask("init-forex-repair", started);
                    } catch (Exception e) {
                        taskTracker.failTask("init-forex-repair", started, e.getMessage());
                        log.error("Forex repair failed", e);
                    }
                });
                return;
            }
            log.info("Forex data exists ({} currencies, {} candles) - skipping init", forexCount, candleCount);
            return;
        }
        log.info("No forex data - starting TCMB + Yahoo fetch");
        TaskInfo started = taskTracker.startTask("init-forex", "Initial forex data fetch (TCMB + Yahoo 5y)");
        taskExecutor.execute(() -> {
            try {
                tcmbForexService.fetchAndSaveTcmbRates();
                yahooForexService.syncAllYahooSnapshots();
                yahooForexService.syncAllYahooCandles();
                taskTracker.completeTask("init-forex", started);
            } catch (Exception e) {
                taskTracker.failTask("init-forex", started, e.getMessage());
                log.error("Forex init failed", e);
            }
        });
    }
}
