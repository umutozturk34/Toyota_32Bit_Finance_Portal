package com.finance.backend.scheduler;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Log4j2
@Component
@RequiredArgsConstructor
public class ForexScheduler {
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "0 0 16 * * *", zone = "${app.timezone}")
    public void runDailyForexUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-forex-full", "Scheduled forex update (TCMB → snapshots → candles)");
        try {
            tcmbForexService.fetchAndSaveTcmbRates();
            yahooForexService.syncAllYahooSnapshots();
            yahooForexService.syncAllYahooCandles();
            taskTracker.completeTask("scheduled-forex-full", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-full", started, e.getMessage());
            log.error("Daily forex update failed", e);
        }
    }
}