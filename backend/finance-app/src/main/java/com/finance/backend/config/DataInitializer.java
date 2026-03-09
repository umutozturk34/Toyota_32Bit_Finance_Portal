package com.finance.backend.config;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executor;
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final MarketDataService marketDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
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
        TaskInfo started = taskTracker.startTask("init-crypto", "Initial crypto data fetch (365 days from CoinGecko)");
        taskExecutor.execute(() -> {
            try {
                marketDataService.fullMarketUpdate();
                taskTracker.completeTask("init-crypto", started);
                log.info("Initial market data loaded successfully!");
            } catch (Exception e) {
                taskTracker.failTask("init-crypto", started, e.getMessage());
                log.error("Initial market data fetch failed: {}", e.getMessage(), e);
            }
        });
        log.info("DataInitializer completed (data fetching in background)");
    }
}
