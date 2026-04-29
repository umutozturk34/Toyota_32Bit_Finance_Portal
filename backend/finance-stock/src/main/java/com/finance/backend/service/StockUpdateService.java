package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.config.StockProperties;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class StockUpdateService implements SnapshotBatchRefresher, CandleBatchRefresher {

    private static final int BATCH_PARALLELISM = 10;

    private final StockCandleRepository stockCandleRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockSnapshotProcessor snapshotProcessor;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final int historyYears;
    private final ZoneId appZone;

    public StockUpdateService(StockCandleRepository stockCandleRepository,
                              MarketCacheService<Stock, StockCandle> stockCacheService,
                              StockSnapshotProcessor snapshotProcessor,
                              TrackedAssetQueryService trackedAssetQueryService,
                              TransactionTemplate transactionTemplate,
                              AppProperties appProperties,
                              StockProperties stockProperties) {
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.snapshotProcessor = snapshotProcessor;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = transactionTemplate;
        this.historyYears = stockProperties.getHistoryYears();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.STOCK;
    }

    @Override
    public void refreshAll() {
        List<String> bistStocks = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.STOCK);
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured");
            return;
        }
        log.info("Starting Yahoo stock sync for {} BIST stocks", bistStocks.size());
        final int[] totalCandles = {0};

        BatchUpdateRunner.Result result = MarketBatchRunner.run(
                bistStocks,
                symbol -> {
                    int candleCount = snapshotProcessor.updateOne(symbol);
                    stockCacheService.refreshHistory(symbol);
                    totalCandles[0] += candleCount;
                },
                Function.identity(),
                log, "Stock", "update", BATCH_PARALLELISM);

        BatchLogHelper.logSummaryWithMetric(log, "Stock update", result, "candles", totalCandles[0]);
        pruneOldCandles(bistStocks);
    }

    @Override
    public void refreshSnapshot(String code) {
        snapshotProcessor.refreshOne(code);
    }

    @Override
    public void refreshCandles(String code) {
        String normalized = CodeNormalizer.upper(code);
        if (normalized.isBlank()) return;
        snapshotProcessor.updateOne(normalized);
        stockCacheService.refreshHistory(normalized);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    private void pruneOldCandles(List<String> symbols) {
        for (String symbol : symbols) {
            CandlePruner.pruneByYears(
                    transactionTemplate,
                    appZone,
                    historyYears,
                    cutoff -> stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, cutoff));
        }
    }
}
