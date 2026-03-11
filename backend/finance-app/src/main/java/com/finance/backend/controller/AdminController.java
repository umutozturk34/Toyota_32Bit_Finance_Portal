package com.finance.backend.controller;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import com.finance.backend.service.FundDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.Executor;
@Log4j2
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final MarketDataService marketDataService;
    private final StockDataService stockDataService;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    @PostMapping("/trigger/crypto/snapshot")
    public ResponseEntity<Map<String, String>> triggerCryptoSnapshotUpdate() {
        return triggerAsync("crypto-snapshot",
                "Crypto snapshot update started in background",
                marketDataService::updateOnlySnapshots);
    }
    @PostMapping("/trigger/crypto/candles")
    public ResponseEntity<Map<String, String>> triggerCryptoCandleUpdate() {
        return triggerAsync("crypto-candles",
                "Crypto candle update started in background",
                marketDataService::updateOnlyCandles);
    }
    @PostMapping("/trigger/crypto/full")
    public ResponseEntity<Map<String, String>> triggerFullCryptoUpdate() {
        return triggerAsync("crypto-full",
                "Full crypto market update started in background",
                marketDataService::fullMarketUpdate);
    }
    @PostMapping("/trigger/stock/snapshot")
    public ResponseEntity<Map<String, String>> triggerStockSnapshotUpdate() {
        return triggerAsync("stock-snapshot",
                "Stock snapshot update started in background",
                stockDataService::updateStockSnapshots);
    }
    @PostMapping("/trigger/stock/candles")
    public ResponseEntity<Map<String, String>> triggerStockCandleUpdate() {
        return triggerAsync("stock-candles",
                "Stock candle update started in background (5 years data)",
                stockDataService::updateStockCandles);
    }
    @PostMapping("/trigger/stock/full")
    public ResponseEntity<Map<String, String>> triggerFullStockUpdate() {
        return triggerAsync("stock-full",
                "Full stock market update started in background",
                () -> {
                    stockDataService.updateStockSnapshots();
                    stockDataService.updateStockCandles();
                });
    }
    @PostMapping("/trigger/forex/snapshot")
    public ResponseEntity<Map<String, String>> triggerForexSnapshotUpdate() {
        return triggerAsync("forex-snapshot",
                "TCMB + Yahoo snapshot update started (~2 min)",
                () -> {
                    tcmbForexService.fetchAndSaveTcmbRates();
                    yahooForexService.syncAllYahooSnapshots();
                });
    }
    @PostMapping("/trigger/forex/candles")
    public ResponseEntity<Map<String, String>> triggerForexCandleUpdate() {
        return triggerAsync("forex-candles",
                "Yahoo Finance candles update started (~10 min, 5 years OHLC)",
                yahooForexService::syncAllYahooCandles);
    }
    @PostMapping("/trigger/forex/full")
    public ResponseEntity<Map<String, String>> triggerFullForexUpdate() {
        return triggerAsync("forex-full",
                "Full forex update started (TCMB + Yahoo snapshots + 5y candles)",
                () -> {
                    tcmbForexService.fetchAndSaveTcmbRates();
                    yahooForexService.syncAllYahooSnapshots();
                    yahooForexService.syncAllYahooCandles();
                });
    }
    @PostMapping("/trigger/fund/snapshot")
    public ResponseEntity<Map<String, String>> triggerFundSnapshotUpdate() {
        return triggerAsync("fund-snapshot",
                "Fund snapshot update started in background",
                fundDataService::updateFundSnapshots);
    }
    @PostMapping("/trigger/fund/candles")
    public ResponseEntity<Map<String, String>> triggerFundCandleUpdate() {
        return triggerAsync("fund-candles",
                "Fund candle update started in background (5 years data)",
                fundDataService::updateFundCandles);
    }
    @PostMapping("/trigger/fund/full")
    public ResponseEntity<Map<String, String>> triggerFullFundUpdate() {
        return triggerAsync("fund-full",
                "Full fund update started in background",
                () -> {
                    fundDataService.updateFundSnapshots();
                    fundDataService.updateFundCandles();
                });
    }
    @GetMapping("/tasks/status")
    public ResponseEntity<Map<String, Object>> getTaskStatus() {
        return ResponseEntity.ok(taskTracker.getStatus());
    }
    private ResponseEntity<Map<String, String>> triggerAsync(String taskType,
                                                             String message,
                                                             Runnable task) {
        if (taskTracker.isRunning(taskType)) {
            log.warn("Rejected duplicate trigger: {}", taskType);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "already_running",
                            "message", taskType + " is already running, please wait",
                            "type", taskType));
        }
        TaskInfo info = taskTracker.startTask(taskType, message);
        log.info("Admin triggered: {}", taskType);
        taskExecutor.execute(() -> {
            try {
                task.run();
                taskTracker.completeTask(taskType, info);
            } catch (Exception e) {
                taskTracker.failTask(taskType, info, e.getMessage());
                log.error("Task failed: {}", taskType, e);
            }
        });
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", message,
                "type", taskType));
    }
}