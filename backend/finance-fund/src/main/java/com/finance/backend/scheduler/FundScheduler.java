package com.finance.backend.scheduler;

import com.finance.backend.model.MarketType;
import com.finance.backend.service.FundDataService;
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
public class FundScheduler {

    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;
    private final Optional<MarketUpdatePort> marketUpdatePort;

    @Scheduled(cron = "${app.scheduler.fund.daily-cron}", zone = "${app.timezone}")
    public void runDailyFundUpdate() {
        taskTracker.runTracked("scheduled-fund-full", "Scheduled fund update (snapshots → candles)", () -> {
            fundDataService.updateFundSnapshots();
            fundDataService.updateFundCandles();
            portfolioSnapshotPort.ifPresent(port -> port.onMarketUpdate(MarketType.FUND));
            marketUpdatePort.ifPresent(port -> port.onMarketDataUpdated(MarketType.FUND));
        });
    }
}
