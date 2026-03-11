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
    @Scheduled(cron = "0 0 16 * * *", zone = "Europe/Istanbul")
    public void runTcmbUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-forex-tcmb", "Scheduled TCMB official rates update");
        try {
            tcmbForexService.fetchAndSaveTcmbRates();
            taskTracker.completeTask("scheduled-forex-tcmb", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-tcmb", started, e.getMessage());
            log.error("TCMB update failed", e);
        }
    }
    @Scheduled(cron = "0 5 16 * * *", zone = "Europe/Istanbul")
    public void runYahooSnapshotUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-forex-snapshot", "Scheduled Yahoo forex snapshot sync");
        try {
            yahooForexService.syncAllYahooSnapshots();
            taskTracker.completeTask("scheduled-forex-snapshot", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-snapshot", started, e.getMessage());
            log.error("Yahoo snapshot sync failed", e);
        }
    }
    @Scheduled(cron = "0 15 16 * * *", zone = "Europe/Istanbul")
    public void runYahooCandleUpdate() {
        TaskInfo started = taskTracker.startTask("scheduled-forex-candles", "Scheduled Yahoo forex candle sync (5y)");
        try {
            yahooForexService.syncAllYahooCandles();
            taskTracker.completeTask("scheduled-forex-candles", started);
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-candles", started, e.getMessage());
            log.error("Yahoo candle sync failed", e);
        }
    }
}