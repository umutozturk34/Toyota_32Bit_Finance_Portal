package com.finance.market.macro.scheduler;

import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.market.macro.service.MacroIndicatorFetchService;
import com.finance.market.macro.service.MacroIndicatorRegistryService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.TaskTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily macro refresh: re-syncs the indicator catalogue from config, fetches new observations, and
 * publishes a {@link MacroIndicatorsUpdatedEvent} when any indicator changed.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class MacroIndicatorScheduler {

    private final MacroIndicatorRegistryService registry;
    private final MacroIndicatorFetchService fetcher;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<EventPublisherPort> events;

    @Scheduled(cron = "${app.scheduler.macro.daily-cron}", zone = "${app.timezone}")
    public void runDailyRefresh() {
        taskTracker.runTracked(
                "scheduled-macro-daily",
                "Scheduled daily macro indicators refresh (17:30)",
                () -> refresh("scheduled-macro-daily"));
    }

    private void refresh(String source) {
        registry.synchronizeFromConfig();
        MacroIndicatorFetchService.FetchOutcome outcome = fetcher.refreshAll();
        log.info("Macro refresh outcome: {} indicators, {} new points, {} changed",
                outcome.indicatorsTouched(), outcome.pointsInserted(), outcome.changedCodes().size());
        if (!outcome.changedCodes().isEmpty()) {
            events.ifAvailable(port -> port.publish(
                    MacroIndicatorsUpdatedEvent.of(source, outcome.changedCodes())));
        }
    }
}
