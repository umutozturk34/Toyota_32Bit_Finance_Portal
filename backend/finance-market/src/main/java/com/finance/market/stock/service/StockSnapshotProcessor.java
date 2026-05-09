package com.finance.market.stock.service;
import com.finance.market.core.cache.MarketCacheService;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.market.stock.client.YahooStockClient;
import com.finance.common.config.AppProperties;
import com.finance.market.stock.config.StockProperties;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.repository.StockCandleRepository;
import com.finance.market.core.util.ApiAssetValidator;
import com.finance.shared.util.CodeNormalizer;
import com.finance.market.core.util.TrackedRefreshRunner;
import com.finance.market.core.util.YahooRangePolicy;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneId;

@Log4j2
@Component
public class StockSnapshotProcessor implements MarketSnapshotProcessor {

    private final YahooStockClient yahooStockClient;
    private final StockCandleRepository stockCandleRepository;
    private final MarketCacheService<Stock> stockCacheService;
    private final StockEntityWriter entityWriter;
    private final TransactionTemplate transactionTemplate;
    private final String fallbackRange;
    private final String chartInterval;
    private final ZoneId appZone;

    public StockSnapshotProcessor(YahooStockClient yahooStockClient,
                                  StockCandleRepository stockCandleRepository,
                                  MarketCacheService<Stock> stockCacheService,
                                  StockEntityWriter entityWriter,
                                  TransactionTemplate transactionTemplate,
                                  AppProperties appProperties,
                                  StockProperties stockProperties) {
        this.yahooStockClient = yahooStockClient;
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.entityWriter = entityWriter;
        this.transactionTemplate = transactionTemplate;
        this.fallbackRange = stockProperties.getChartRange();
        this.chartInterval = stockProperties.getChartInterval();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public int updateOne(String symbol) {
        String range = stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc(symbol)
                .map(lastCandle -> YahooRangePolicy.fromLastCandle(lastCandle.getCandleDate(), appZone, fallbackRange))
                .orElse(fallbackRange);
        YahooChartFullResult<YahooStockQuoteDto> result = yahooStockClient.fetchStockChartFull(symbol, range, chartInterval, true);
        boolean hasFreshQuote = result.quote() != null && result.quote().currentPrice() != null;
        if (!hasFreshQuote && result.candles().isEmpty()) {
            log.info("No quote and no candles for {} (likely market closed) - skipping", symbol);
            return 0;
        }
        return transactionTemplate.execute(status -> {
            Stock stock = hasFreshQuote
                    ? entityWriter.saveSnapshot(result.quote(), symbol)
                    : entityWriter.findExisting(symbol);
            if (stock == null) {
                log.info("No fresh quote and no existing snapshot for {} - skipping", symbol);
                return 0;
            }
            int saved = result.candles().isEmpty()
                    ? 0
                    : entityWriter.upsertCandles(symbol, stock, result.candles());
            entityWriter.refreshChangePercentFromCandles(stock);
            stockCacheService.putSnapshot(symbol, stock);
            return saved;
        });
    }

    public void refreshOne(String symbol) {
        TrackedRefreshRunner.refreshSnapshot(symbol, CodeNormalizer::upper, normalized -> {
            updateOne(normalized);
            return true;
        }, log, "stock");
    }

    public boolean exists(String symbol) {
        return ApiAssetValidator.validate(symbol, true, sym -> {
            YahooChartFullResult<YahooStockQuoteDto> result = yahooStockClient.fetchStockChartFull(sym, "1d", chartInterval, true);
            return result.quote() != null && result.quote().currentPrice() != null;
        }, log, "Stock");
    }
}
