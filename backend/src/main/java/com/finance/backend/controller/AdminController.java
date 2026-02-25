package com.finance.backend.controller;
import com.finance.backend.service.MarketDataService;
import com.finance.backend.service.StockDataService;
import com.finance.backend.service.TcmbForexService;
import com.finance.backend.service.YahooForexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final MarketDataService marketDataService;
    private final StockDataService stockDataService;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final Executor taskExecutor;
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();
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
    @GetMapping("/tasks/status")
    public ResponseEntity<Map<String, Object>> getRunningTasks() {
        return ResponseEntity.ok(Map.of(
                "runningTasks", runningTasks,
                "count", runningTasks.size()));
    }
    private ResponseEntity<Map<String, String>> triggerAsync(String taskType,
                                                             String message,
                                                             Runnable task) {
        if (!runningTasks.add(taskType)) {
            log.warn("Rejected duplicate trigger: {}", taskType);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "already_running",
                            "message", taskType + " is already running, please wait",
                            "type", taskType));
        }
        log.info("Admin triggered: {}", taskType);
        taskExecutor.execute(() -> {
            try {
                task.run();
                log.info("Task completed: {}", taskType);
            } catch (Exception e) {
                log.error("Task failed: {}", taskType, e);
            } finally {
                runningTasks.remove(taskType);
            }
        });
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", message,
                "type", taskType));
    }
}