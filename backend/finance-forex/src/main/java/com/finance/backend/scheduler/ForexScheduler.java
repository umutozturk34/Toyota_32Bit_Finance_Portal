package com.finance.backend.scheduler;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class ForexScheduler {
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final TaskTrackingService taskTracker;
    @Scheduled(cron = "0 0 16 * * *", zone = "Europe/Istanbul")
    public void runTcmbUpdate() {
        log.info("[SCHEDULER] Starting TCMB official rates update...");
        TaskInfo started = taskTracker.startTask("scheduled-forex-tcmb", "Scheduled TCMB official rates update");
        try {
            tcmbForexService.fetchAndSaveTcmbRates();
            taskTracker.completeTask("scheduled-forex-tcmb", started);
            log.info("[SCHEDULER] TCMB update successful.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-tcmb", started, e.getMessage());
            log.error("[SCHEDULER-ERROR] TCMB update failed: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 5 16 * * *", zone = "Europe/Istanbul")
    public void runYahooSnapshotUpdate() {
        log.info("[SCHEDULER] Starting Yahoo Finance snapshot sync...");
        TaskInfo started = taskTracker.startTask("scheduled-forex-snapshot", "Scheduled Yahoo forex snapshot sync");
        try {
            yahooForexService.syncAllYahooSnapshots();
            taskTracker.completeTask("scheduled-forex-snapshot", started);
            log.info("[SCHEDULER] Yahoo snapshot sync successful.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-snapshot", started, e.getMessage());
            log.error("[SCHEDULER-ERROR] Yahoo snapshot sync failed: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 15 16 * * *", zone = "Europe/Istanbul")
    public void runYahooCandleUpdate() {
        log.info("[SCHEDULER] Starting Yahoo Finance heavy-duty candle sync...");
        TaskInfo started = taskTracker.startTask("scheduled-forex-candles", "Scheduled Yahoo forex candle sync (5y)");
        try {
            yahooForexService.syncAllYahooCandles();
            taskTracker.completeTask("scheduled-forex-candles", started);
            log.info("[SCHEDULER] Yahoo candle sync successful.");
        } catch (Exception e) {
            taskTracker.failTask("scheduled-forex-candles", started, e.getMessage());
            log.error("[SCHEDULER-ERROR] Yahoo candle sync failed: {}", e.getMessage());
        }
    }
}