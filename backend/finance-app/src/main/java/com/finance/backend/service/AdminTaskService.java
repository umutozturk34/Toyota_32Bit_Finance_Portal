package com.finance.backend.service;

import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.dto.response.TaskTriggerResponse;
import com.finance.backend.exception.TaskAlreadyRunningException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Log4j2
@Service
@RequiredArgsConstructor
public class AdminTaskService {

    private final MarketDataService marketDataService;
    private final StockDataService stockDataService;
    private final TcmbForexService tcmbForexService;
    private final YahooForexService yahooForexService;
    private final FundDataService fundDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    public TaskTriggerResponse triggerCryptoSnapshot() {
        return executeTask("crypto-snapshot",
                "Crypto snapshot update started in background",
                marketDataService::updateOnlySnapshots);
    }

    public TaskTriggerResponse triggerCryptoCandles() {
        return executeTask("crypto-candles",
                "Crypto candle update started in background",
                marketDataService::updateOnlyCandles);
    }

    public TaskTriggerResponse triggerCryptoFull() {
        return executeTask("crypto-full",
                "Full crypto market update started in background",
                marketDataService::fullMarketUpdate);
    }

    public TaskTriggerResponse triggerStockSnapshot() {
        return executeTask("stock-snapshot",
                "Stock snapshot update started in background",
                stockDataService::updateStockSnapshots);
    }

    public TaskTriggerResponse triggerStockCandles() {
        return executeTask("stock-candles",
                "Stock candle update started in background (5 years data)",
                stockDataService::updateStockCandles);
    }

    public TaskTriggerResponse triggerStockFull() {
        return executeTask("stock-full",
                "Full stock market update started in background",
                () -> {
                    stockDataService.updateStockSnapshots();
                    stockDataService.updateStockCandles();
                });
    }

    public TaskTriggerResponse triggerForexSnapshot() {
        return executeTask("forex-snapshot",
                "TCMB + Yahoo snapshot update started (~2 min)",
                () -> {
                    tcmbForexService.fetchAndSaveTcmbRates();
                    yahooForexService.syncAllYahooSnapshots();
                });
    }

    public TaskTriggerResponse triggerForexCandles() {
        return executeTask("forex-candles",
                "Yahoo Finance candles update started (~10 min, 5 years OHLC)",
                yahooForexService::syncAllYahooCandles);
    }

    public TaskTriggerResponse triggerForexFull() {
        return executeTask("forex-full",
                "Full forex update started (TCMB + Yahoo snapshots + 5y candles)",
                () -> {
                    tcmbForexService.fetchAndSaveTcmbRates();
                    yahooForexService.syncAllYahooSnapshots();
                    yahooForexService.syncAllYahooCandles();
                });
    }

    public TaskTriggerResponse triggerFundSnapshot() {
        return executeTask("fund-snapshot",
                "Fund snapshot update started in background",
                fundDataService::updateFundSnapshots);
    }

    public TaskTriggerResponse triggerFundCandles() {
        return executeTask("fund-candles",
                "Fund candle update started in background (5 years data)",
                fundDataService::updateFundCandles);
    }

    public TaskTriggerResponse triggerFundFull() {
        return executeTask("fund-full",
                "Full fund update started in background",
                () -> {
                    fundDataService.updateFundSnapshots();
                    fundDataService.updateFundCandles();
                });
    }

    public TaskStatusResponse getTaskStatus() {
        return taskTracker.getTypedStatus();
    }

    private TaskTriggerResponse executeTask(String taskType, String message, Runnable task) {
        if (taskTracker.isRunning(taskType)) {
            throw new TaskAlreadyRunningException(taskType);
        }

        TaskTrackingService.TaskInfo info = taskTracker.startTask(taskType, message);
        log.info("Task started: {}", taskType);

        taskExecutor.execute(() -> {
            try {
                task.run();
                taskTracker.completeTask(taskType, info);
                log.info("Task completed: {}", taskType);
            } catch (Exception e) {
                taskTracker.failTask(taskType, info, e.getMessage());
                log.error("Task failed: {}", taskType, e);
            }
        });

        return TaskTriggerResponse.started(taskType, message);
    }
}
