package com.finance.backend.scheduler;
import com.finance.backend.model.MarketType;
import com.finance.backend.service.CryptoDataService;
import com.finance.backend.service.MarketUpdatePort;
import com.finance.backend.service.PortfolioSnapshotPort;
import com.finance.backend.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Log4j2
@Component
@RequiredArgsConstructor
public class CryptoScheduler {
    private final CryptoDataService marketDataService;
    private final TaskTrackingService taskTracker;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Scheduled(cron = "${app.scheduler.crypto.morning-cron}", zone = "${app.timezone}")
    public void runMorningCryptoUpdate() {
        executeCryptoUpdate("scheduled-crypto-morning", "Scheduled morning crypto update (09:00)");
    }

    @Scheduled(cron = "${app.scheduler.crypto.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonCryptoUpdate() {
        executeCryptoUpdate("scheduled-crypto-afternoon", "Scheduled afternoon crypto update (15:00)");
    }

    @Scheduled(cron = "${app.scheduler.crypto.evening-cron}", zone = "${app.timezone}")
    public void runEveningCryptoUpdate() {
        executeCryptoUpdate("scheduled-crypto-evening", "Scheduled evening crypto update (21:00)");
    }

    private void executeCryptoUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            marketDataService.updateOnlySnapshots();
            marketDataService.updateOnlyCandles();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(MarketType.CRYPTO));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(MarketType.CRYPTO));
        });
    }
}
