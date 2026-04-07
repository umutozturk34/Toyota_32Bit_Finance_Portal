package com.finance.backend.service;

import com.finance.backend.dto.response.TaskStatusResponse;
import com.finance.backend.dto.response.TaskTriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Optional;
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
    private final BondDataService bondDataService;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;
    private final Optional<PortfolioSnapshotPort> portfolioSnapshotPort;

    public TaskTriggerResponse triggerCryptoSnapshot() {
        return executeTask("crypto-snapshot",
                "Crypto snapshot update started in background",
                () -> {
                    marketDataService.updateOnlySnapshots();
                    triggerPortfolioSnapshot("CRYPTO");
                });
    }

    public TaskTriggerResponse triggerCryptoCandles() {
        return executeTask("crypto-candles",
                "Crypto candle update started in background",
                marketDataService::updateOnlyCandles);
    }

    public TaskTriggerResponse triggerCryptoFull() {
        return executeTask("crypto-full",
                "Full crypto market update started in background",
                () -> {
                    marketDataService.fullMarketUpdate();
                    triggerPortfolioSnapshot("CRYPTO");
                });
    }

    public TaskTriggerResponse triggerStockSnapshot() {
        return executeTask("stock-snapshot",
                "Stock snapshot update started in background",
                () -> {
                    stockDataService.updateStockSnapshots();
                    triggerPortfolioSnapshot("STOCK");
                });
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
                    triggerPortfolioSnapshot("STOCK");
                });
    }

    public TaskTriggerResponse triggerForexSnapshot() {
        return executeTask("forex-snapshot",
                "TCMB + Yahoo snapshot update started (~2 min)",
                () -> {
                    tcmbForexService.fetchAndSaveTcmbRates();
                    yahooForexService.syncAllYahooSnapshots();
                    triggerPortfolioSnapshot("FOREX");
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
                    triggerPortfolioSnapshot("FOREX");
                });
    }

    public TaskTriggerResponse triggerFundSnapshot() {
        return executeTask("fund-snapshot",
                "Fund snapshot update started in background",
                () -> {
                    fundDataService.updateFundSnapshots();
                    triggerPortfolioSnapshot("FUND");
                });
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
                    triggerPortfolioSnapshot("FUND");
                });
    }

    public TaskTriggerResponse triggerBondUpdate() {
        return executeTask("bond-update",
                "Bond update started in background",
                bondDataService::updateBonds);
    }

    public TaskTriggerResponse triggerNewsUpdate() {
        return executeTask("news-update",
                "News feed update started in background",
                newsDataService::updateNews);
    }

    public TaskStatusResponse getTaskStatus() {
        return taskTracker.getTypedStatus();
    }

    private void triggerPortfolioSnapshot(String assetType) {
        portfolioSnapshotPort.ifPresent(port -> {
            try {
                port.onMarketUpdate(assetType);
                log.info("Portfolio snapshot triggered for {}", assetType);
            } catch (Exception e) {
                log.warn("Portfolio snapshot failed for {}: {}", assetType, e.getMessage());
            }
        });
    }

    private TaskTriggerResponse executeTask(String taskType, String message, Runnable task) {
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
