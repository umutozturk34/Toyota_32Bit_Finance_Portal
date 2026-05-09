package com.finance.market.stock.service;
import com.finance.market.stock.model.Stock;

import com.finance.market.core.service.MarketRefresher;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.market.stock.config.StockProperties;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.util.BatchLogHelper;
import com.finance.common.util.BatchUpdateRunner;
import com.finance.common.util.CodeNormalizer;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class StockUpdateService implements MarketRefresher {

    private final StockSnapshotProcessor snapshotProcessor;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final int batchMinSample;

    public StockUpdateService(StockSnapshotProcessor snapshotProcessor,
                              TrackedAssetQueryService trackedAssetQueryService,
                              StockProperties stockProperties) {
        this.snapshotProcessor = snapshotProcessor;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.batchMinSample = stockProperties.getBatchMinSample();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.STOCK;
    }

    @Override
    public void refreshAll() {
        List<String> bistStocks = trackedAssetQueryService.getCodes(TrackedAssetType.STOCK);
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
                    totalCandles[0] += candleCount;
                },
                Function.identity(),
                log, "Stock", "update", batchMinSample);

        BatchLogHelper.logSummaryWithMetric(log, "Stock update", result, "candles", totalCandles[0]);
    }

    @Override
    public void refresh(String code) {
        String normalized = CodeNormalizer.upper(code);
        if (normalized.isBlank()) return;
        snapshotProcessor.updateOne(normalized);
    }

    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }
}
