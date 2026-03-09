package com.finance.backend.config;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.service.StockDataService;
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
@Order(2) 
@RequiredArgsConstructor
public class StockDataInitializer implements CommandLineRunner {
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockDataService stockDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
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
        TaskInfo started = taskTracker.startTask("init-stock", "Initial stock data fetch (5y from Yahoo Finance)");
        taskExecutor.execute(() -> {
            try {
                log.info("Step 1/2: Fetching stock snapshots...");
                stockDataService.updateStockSnapshots();
                log.info("Step 2/2: Fetching 5-year candle data (this may take 10-15 minutes)...");
                stockDataService.updateStockCandles();
                taskTracker.completeTask("init-stock", started);
                log.info("Initial stock data loaded successfully!");
            } catch (Exception e) {
                taskTracker.failTask("init-stock", started, e.getMessage());
                log.error("Initial stock data fetch failed: {}", e.getMessage(), e);
            }
        });
        log.info("StockDataInitializer completed (data fetching in background)");
    }
}
