package com.finance.backend.service;

import com.finance.backend.client.YahooStockClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.repository.StockRepository;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service

public class StockDataService {
    private final ZoneId appZone;
    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final MarketConstants marketConstants;
    private final TransactionTemplate transactionTemplate;
    private final int minCandlesForIncremental;
    private final int historyYears;

    public StockDataService(YahooStockClient yahooStockClient,
            StockMapper stockMapper,
            StockRepository stockRepository,
            StockCandleRepository stockCandleRepository,
            MarketCacheService<Stock, StockCandle> stockCacheService,
            MarketConstants marketConstants,
            PlatformTransactionManager transactionManager,
            AppProperties appProperties) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.marketConstants = marketConstants;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.minCandlesForIncremental = appProperties.getStock().getMinCandlesForIncremental();
        this.historyYears = appProperties.getStock().getHistoryYears();
        this.appZone = ZoneId.of(appProperties.getTimezone());
    }

    public void updateStockSnapshots() {
        List<String> bistStocks = marketConstants.getTrackedBistStocks();
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting snapshot update for {} BIST stocks", bistStocks.size());
        int successCount = 0;
        int failCount = 0;
        List<String> failedSymbols = new ArrayList<>();
        for (String symbol : bistStocks) {
            try {
                Stock stock = transactionTemplate.execute(status -> updateSingleStockSnapshot(symbol));
                stockCacheService.putSnapshot(symbol, stock);
                successCount++;
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update snapshot for {}: {}", symbol, e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedSymbols, "snapshot", 10);
            }
        }
        log.info("Stock snapshot update: {} success, {} failed", successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
    }

    private Stock updateSingleStockSnapshot(String symbol) {
        YahooStockQuoteDto dto = yahooStockClient.fetchQuote(symbol);
        if (dto == null) {
            throw new BusinessException(
                    "Failed to fetch stock data from external API: " + symbol,
                    "EXTERNAL_API_ERROR");
        }
        if (dto.currentPrice() == null) {
            throw new BusinessException(
                    "Invalid stock data received - missing price for: " + symbol,
                    "INVALID_EXTERNAL_DATA");
        }
        LocalDateTime now = LocalDateTime.now();
        Stock stock = stockRepository.findById(symbol).orElse(null);
        if (stock != null) {
            stockMapper.updateEntityFromDto(stock, dto, now);
        } else {
            stock = stockMapper.toEntity(dto, now);
        }
        stockRepository.save(stock);
        return stock;
    }

    public void updateStockCandles() {
        List<String> bistStocks = marketConstants.getTrackedBistStocks();
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting candle update for {} BIST stocks", bistStocks.size());
        int totalCandles = 0;
        int successCount = 0;
        int failCount = 0;
        List<String> failedSymbols = new ArrayList<>();
        for (String symbol : bistStocks) {
            try {
                int candleCount = transactionTemplate.execute(status -> updateCandlesForStock(symbol));
                stockCacheService.refreshHistory(symbol);
                totalCandles += candleCount;
                successCount++;
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update candles for {} (transaction rolled back): {}", symbol, e.getMessage(), e);
                BatchFailureGuard.check(successCount, failCount, failedSymbols, "candle", 10);
            }
        }
        log.info("Stock candle update: {} total, {} success, {} failed", totalCandles, successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
    }

    private int updateCandlesForStock(String symbol) {
        Stock stock = stockRepository.getReferenceById(symbol);
        long existingCount = stockCandleRepository.countByStockSymbol(symbol);
        String range = existingCount < minCandlesForIncremental
                ? historyYears + "y"
                : stockCandleRepository.findFirstByStockSymbolOrderByCandleDateDesc(symbol)
                        .map(lastCandle -> toYahooRange(lastCandle.getCandleDate()))
                        .orElse(historyYears + "y");
        log.debug("{} - existing: {}, range: {}", symbol, existingCount, range);
        List<YahooCandleDto> candleDtos = yahooStockClient.fetchCandles(symbol, range, "1d", true);
        if (candleDtos.isEmpty()) {
            log.warn("{} - No valid candle data", symbol);
            return 0;
        }
        List<LocalDateTime> dates = candleDtos.stream().map(YahooCandleDto::candleDate).toList();
        Map<LocalDateTime, StockCandle> existingMap = stockCandleRepository
                .findByStockSymbolAndCandleDateIn(symbol, dates)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getCandleDate().truncatedTo(ChronoUnit.DAYS),
                        Function.identity(),
                        (a, b) -> a));
        List<StockCandle> toSave = new ArrayList<>(candleDtos.size());
        int newCount = 0;
        int updateCount = 0;
        for (YahooCandleDto dto : candleDtos) {
            StockCandle existing = existingMap.get(dto.candleDate());
            if (existing != null) {
                stockMapper.updateCandleEntity(existing, dto);
                updateCount++;
            } else {
                toSave.add(stockMapper.toCandleEntity(dto, stock));
                newCount++;
            }
        }
        if (!toSave.isEmpty()) {
            stockCandleRepository.saveAll(toSave);
        }
        if (newCount > 0 || updateCount > 0) {
            log.debug("{} - {} new, {} updated", symbol, newCount, updateCount);
        }
        if (range.endsWith("y")) {
            LocalDateTime cutoff = LocalDateTime.now(appZone).minusYears(historyYears);
            stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, cutoff);
        }
        return newCount + updateCount;
    }

    private String toYahooRange(LocalDateTime lastCandleDate) {
        long gapDays = ChronoUnit.DAYS.between(lastCandleDate.toLocalDate(), LocalDate.now(appZone));
        if (gapDays <= 5)
            return "5d";
        if (gapDays <= 30)
            return "1mo";
        if (gapDays <= 90)
            return "3mo";
        if (gapDays <= 180)
            return "6mo";
        if (gapDays <= 365)
            return "1y";
        if (gapDays <= 730)
            return "2y";
        return historyYears + "y";
    }
}
