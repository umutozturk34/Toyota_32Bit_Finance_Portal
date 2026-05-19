package com.finance.market.macro.scheduler;

import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class MacroIndicatorScheduler {

    private final MacroIndicatorRegistryService registry;
    private final MacroIndicatorFetchService fetcher;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "${app.scheduler.macro.daily-cron}", zone = "${app.timezone}")
    public void runDailyRefresh() {
        taskTracker.runTracked(
                "scheduled-macro-daily",
                "Scheduled daily macro indicators refresh (17:30)",
                this::refresh);
    }

    private void refresh() {
        registry.synchronizeFromConfig();
        MacroIndicatorFetchService.FetchOutcome outcome = fetcher.refreshAll();
        log.info("Macro refresh outcome: {} indicators, {} new points",
                outcome.indicatorsTouched(), outcome.pointsInserted());
    }
}
