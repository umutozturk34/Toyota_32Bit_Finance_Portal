package com.finance.backend.config;

import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.service.FundDataService;
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
@Order(4)
@RequiredArgsConstructor
public class FundDataInitializer implements CommandLineRunner {

    private final FundRepository fundRepository;
    private final FundCandleRepository fundCandleRepository;
    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    @Override
    public void run(String... args) {
        log.info("Running FundDataInitializer (Smart Mode)...");

        long fundCount = fundRepository.count();
        long candleCount = fundCandleRepository.count();

        if (fundCount > 0 && candleCount > 0) {
            log.info("Existing fund data found ({} funds, {} candles) - skipping initial fetch",
                    fundCount, candleCount);
            log.info("Next update will run at scheduled time (11:30 Istanbul)");
            return;
        }

        log.info("No fund data found - starting initial fetch from TEFAS API...");
        TaskInfo started = taskTracker.startTask("init-fund", "Initial fund data fetch (TEFAS 5y)");
        taskExecutor.execute(() -> {
            try {
                log.info("Step 1/2: Fetching fund snapshots (BYF + YAT)...");
                fundDataService.updateFundSnapshots();

                log.info("Step 2/2: Fetching 5-year candle data (this may take a while)...");
                fundDataService.updateFundCandles();

                taskTracker.completeTask("init-fund", started);
                log.info("Initial fund data loaded successfully!");
            } catch (Exception e) {
                taskTracker.failTask("init-fund", started, e.getMessage());
                log.error("Initial fund data fetch failed: {}", e.getMessage(), e);
            }
        });

        log.info("FundDataInitializer completed (data fetching in background)");
    }
}
