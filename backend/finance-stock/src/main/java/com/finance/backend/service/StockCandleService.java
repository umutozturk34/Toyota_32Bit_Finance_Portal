package com.finance.backend.service;

import com.finance.backend.client.YahooStockClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CandleBatchUpsertTemplate;
import com.finance.backend.util.CandlePruner;
import com.finance.backend.util.YahooRangePolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

@Log4j2
@Service
public class StockCandleService implements CandleBatchRefresher {

    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final int historyYears;
    private final ZoneId appZone;

    public StockCandleService(YahooStockClient yahooStockClient,
                              StockMapper stockMapper,
                              StockRepository stockRepository,
                              StockCandleRepository stockCandleRepository,
                              MarketCacheService<Stock, StockCandle> stockCacheService,
                              TrackedAssetQueryService trackedAssetQueryService,
                              PlatformTransactionManager transactionManager,
                              AppProperties appProperties) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.historyYears = appProperties.getStock().getHistoryYears();
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
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting candle update for {} BIST stocks", bistStocks.size());
        final int[] totalCandles = {0};

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                bistStocks,
                symbol -> {
                    int candleCount = transactionTemplate.execute(status -> updateCandlesForStock(symbol));
                    stockCacheService.refreshHistory(symbol);
                    totalCandles[0] += candleCount;
                },
                Function.identity(),
                "candle",
                10,
                (symbol, e) -> log.error("Failed to update candles for {} (transaction rolled back): {}", symbol, e.getMessage(), e),
                null,
                null);

        BatchLogHelper.logSummaryWithMetric(log, "Stock candle update", result, "total", totalCandles[0]);
    }

    public void refreshTrackedStockCandles(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return;
        }
        int candleCount = transactionTemplate.execute(status -> updateCandlesForStock(normalized));
        stockCacheService.refreshHistory(normalized);
        log.info("Refreshed tracked stock candles for {} ({} changed)", normalized, candleCount);
    }

    private int updateCandlesForStock(String symbol) {
        Stock stock = stockRepository.getReferenceById(symbol);
        String fallbackRange = historyYears + "y";
        String range = stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc(symbol)
                .map(lastCandle -> YahooRangePolicy.fromLastCandle(lastCandle.getCandleDate(), appZone, fallbackRange))
                .orElse(fallbackRange);
        log.debug("{} - range: {}", symbol, range);
        List<YahooCandleDto> candleDtos = yahooStockClient.fetchCandles(symbol, range, "1d", true);
        if (candleDtos.isEmpty()) {
            log.warn("{} - No valid candle data", symbol);
            return 0;
        }
        CandleBatchUpsertTemplate.UpsertResult<StockCandle> upsertResult = CandleBatchUpsertTemplate.upsert(
                candleDtos,
                dto -> dto.candleDate().truncatedTo(ChronoUnit.DAYS),
                keys -> stockCandleRepository.findByStockSymbolAndCandleDateIn(symbol, keys),
                candle -> candle.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                stockMapper::updateCandleEntity,
                dto -> stockMapper.toCandleEntity(dto, stock));

        if (!upsertResult.newEntities().isEmpty()) {
            stockCandleRepository.saveAll(upsertResult.newEntities());
        }
        if (upsertResult.insertCount() > 0 || upsertResult.updateCount() > 0) {
            log.debug("{} - {} new, {} updated", symbol, upsertResult.insertCount(), upsertResult.updateCount());
        }
        if (range.endsWith("y")) {
            CandlePruner.pruneByYears(
                    transactionTemplate,
                    appZone,
                    historyYears,
                    cutoff -> stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, cutoff));
        }
        return upsertResult.totalChanged();
    }
}
