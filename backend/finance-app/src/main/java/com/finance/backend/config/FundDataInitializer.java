package com.finance.backend.config;

import com.finance.backend.repository.FundCandleRepository;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.service.FundDataService;
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
        long fundCount = fundRepository.count();
        long candleCount = fundCandleRepository.count();

        if (fundCount > 0 && candleCount > 0) {
            log.info("Fund data exists ({} funds, {} candles) - skipping init", fundCount, candleCount);
            return;
        }

        log.info("No fund data - starting TEFAS fetch");
        TaskInfo started = taskTracker.startTask("init-fund", "Initial fund data fetch (TEFAS 5y)");
        taskExecutor.execute(() -> {
            try {
                fundDataService.updateFundSnapshots();
                fundDataService.updateFundCandles();
                taskTracker.completeTask("init-fund", started);
            } catch (Exception e) {
                taskTracker.failTask("init-fund", started, e.getMessage());
                log.error("Fund init failed", e);
            }
        });
    }
}
