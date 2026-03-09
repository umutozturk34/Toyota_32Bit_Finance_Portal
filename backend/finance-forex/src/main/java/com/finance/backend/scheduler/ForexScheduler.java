package com.finance.backend.scheduler;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
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
    @Scheduled(cron = "0 0 16 * * *", zone = "Europe/Istanbul")
    public void runTcmbUpdate() {
        log.info("[SCHEDULER] Starting TCMB official rates update...");
        try {
            tcmbForexService.fetchAndSaveTcmbRates();
            log.info("[SCHEDULER] TCMB update successful.");
        } catch (Exception e) {
            log.error("[SCHEDULER-ERROR] TCMB update failed: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 5 16 * * *", zone = "Europe/Istanbul")
    public void runYahooSnapshotUpdate() {
        log.info("[SCHEDULER] Starting Yahoo Finance snapshot sync...");
        try {
            yahooForexService.syncAllYahooSnapshots();
            log.info("[SCHEDULER] Yahoo snapshot sync successful.");
        } catch (Exception e) {
            log.error("[SCHEDULER-ERROR] Yahoo snapshot sync failed: {}", e.getMessage());
        }
    }
    @Scheduled(cron = "0 15 16 * * *", zone = "Europe/Istanbul")
    public void runYahooCandleUpdate() {
        log.info("[SCHEDULER] Starting Yahoo Finance heavy-duty candle sync...");
        try {
            yahooForexService.syncAllYahooCandles();
            log.info("[SCHEDULER] Yahoo candle sync successful.");
        } catch (Exception e) {
            log.error("[SCHEDULER-ERROR] Yahoo candle sync failed: {}", e.getMessage());
        }
    }
}