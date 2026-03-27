package com.finance.backend.config;

import com.finance.backend.repository.BondRepository;
import com.finance.backend.service.BondDataService;
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
@Order(5)
@RequiredArgsConstructor
public class BondDataInitializer implements CommandLineRunner {
    private final BondRepository bondRepository;
    private final BondDataService bondDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    @Override
    public void run(String... args) {
        long bondCount = bondRepository.count();
        if (bondCount > 0) {
            log.info("Bond data exists ({} bonds) - skipping init", bondCount);
            return;
        }
        log.info("No bond data - starting EVDS bond fetch");
        TaskInfo started = taskTracker.startTask("init-bond", "Initial bond data fetch from EVDS");
        taskExecutor.execute(() -> {
            try {
                log.info("Bond init: starting snapshot update");
                bondDataService.updateBonds();
                taskTracker.completeTask("init-bond", started);
                log.info("Bond init: completed successfully");
            } catch (Throwable e) {
                taskTracker.failTask("init-bond", started, e.getMessage());
                log.error("Bond init failed: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            }
        });
    }
}
