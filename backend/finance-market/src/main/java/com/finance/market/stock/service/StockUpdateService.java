package com.finance.market.stock.service;

import com.finance.market.core.service.MarketRefresher;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.stock.client.IsYatirimStockListProvider;
import com.finance.market.stock.config.StockProperties;
import com.finance.common.exception.BusinessException;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import com.finance.shared.util.CodeNormalizer;
import com.finance.market.core.util.MarketBatchRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * Refreshes BIST stocks: first discovers and auto-tracks any new tickers from İş Yatırım, then
 * updates every tracked stock's snapshot/candles from Yahoo (per-symbol failures isolated).
 */
@Log4j2
@Service
public class StockUpdateService implements MarketRefresher {

    private final StockSnapshotProcessor snapshotProcessor;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final IsYatirimStockListProvider stockListProvider;
    private final StockProperties.Discovery discovery;
    private final int batchMinSample;

    public StockUpdateService(StockSnapshotProcessor snapshotProcessor,
                              TrackedAssetQueryService trackedAssetQueryService,
                              TrackedAssetCommandService trackedAssetCommandService,
                              IsYatirimStockListProvider stockListProvider,
                              StockProperties stockProperties) {
        this.snapshotProcessor = snapshotProcessor;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.trackedAssetCommandService = trackedAssetCommandService;
        this.stockListProvider = stockListProvider;
        this.discovery = stockProperties.getDiscovery();
        this.batchMinSample = stockProperties.getBatchMinSample();
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.STOCK;
    }

    /**
     * Full BIST refresh cycle: first auto-tracks newly listed İş Yatırım tickers, then updates every
     * tracked stock from Yahoo in a batch where per-symbol failures are isolated and counted.
     *
     * @throws com.finance.common.exception.BusinessException if no BIST stocks are configured for tracking
     */
    @Override
    public void refreshAll() {
        discoverAndTrack();

        List<String> bistStocks = trackedAssetQueryService.getCodes(TrackedAssetType.STOCK);
        if (bistStocks.isEmpty()) {
            log.error("No BIST stocks configured for tracking");
            throw new BusinessException("error.market.stockNoneTracked");
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

    /** Refreshes a single stock by code; the code is upper-normalized first and blank codes are no-ops. */
    @Override
    public void refresh(String code) {
        String normalized = CodeNormalizer.upper(code);
        if (normalized.isBlank()) return;
        snapshotProcessor.updateOne(normalized);
    }

    /** Whether a stock with the given code is already known to the snapshot store. */
    public boolean exists(String code) {
        return snapshotProcessor.exists(code);
    }

    /** Auto-tracks any İş Yatırım ticker (with the configured suffix) not already tracked. */
    private void discoverAndTrack() {
        List<String> tickers = stockListProvider.fetchTickers();
        if (tickers.isEmpty()) {
            log.warn("İş Yatırım returned no tickers; skipping auto-track step");
            return;
        }
        String suffix = discovery.getSuffix();
        int sortOrder = discovery.getAutoTrackSortOrder();
        java.util.Set<String> existing = new java.util.HashSet<>(
                trackedAssetQueryService.getCodes(TrackedAssetType.STOCK));
        int inserted = 0;
        for (String ticker : tickers) {
            String code = ticker + suffix;
            if (existing.contains(code)) continue;
            try {
                trackedAssetCommandService.autoTrack(TrackedAssetType.STOCK, code, null, sortOrder);
                inserted++;
            } catch (Exception e) {
                log.warn("autoTrack failed for {}: {}", code, e.getMessage());
            }
        }
        log.info("İş Yatırım discovery: {} tickers fetched, {} newly tracked", tickers.size(), inserted);
    }
}
