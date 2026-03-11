package com.finance.backend.config;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.concurrent.Executor;
@Log4j2
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
        long cryptoCount = cryptoRepository.count();
        long candleCount = cryptoCandleRepository.count();
        if (cryptoCount > 0 && candleCount > 0) {
            log.info("Crypto data exists ({} cryptos, {} candles) - skipping init", cryptoCount, candleCount);
            return;
        }
        log.info("No crypto data - starting 365-day CoinGecko fetch");
        TaskInfo started = taskTracker.startTask("init-crypto", "Initial crypto data fetch (365 days from CoinGecko)");
        taskExecutor.execute(() -> {
            try {
                marketDataService.fullMarketUpdate();
                taskTracker.completeTask("init-crypto", started);
            } catch (Exception e) {
                taskTracker.failTask("init-crypto", started, e.getMessage());
                log.error("Crypto init failed", e);
            }
        });
    }
}
