package com.finance.backend.config;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.service.StockDataService;
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
        long stockCount = stockRepository.count();
        long candleCount = stockCandleRepository.count();
        if (stockCount > 0 && candleCount > 0) {
            log.info("Stock data exists ({} stocks, {} candles) - skipping init", stockCount, candleCount);
            return;
        }
        log.info("No stock data - starting 5-year Yahoo Finance fetch");
        TaskInfo started = taskTracker.startTask("init-stock", "Initial stock data fetch (5y from Yahoo Finance)");
        taskExecutor.execute(() -> {
            try {
                stockDataService.updateStockSnapshots();
                stockDataService.updateStockCandles();
                taskTracker.completeTask("init-stock", started);
            } catch (Exception e) {
                taskTracker.failTask("init-stock", started, e.getMessage());
                log.error("Stock init failed", e);
            }
        });
    }
}
