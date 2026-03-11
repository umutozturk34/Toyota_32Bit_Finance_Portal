package com.finance.backend.service;
import com.finance.backend.client.YahooStockClient;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1200;
    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final MarketConstants marketConstants;
    private final StockDataService self;
    public StockDataService(YahooStockClient yahooStockClient,
                            StockMapper stockMapper,
                            StockRepository stockRepository,
                            StockCandleRepository stockCandleRepository,
                            MarketCacheService<Stock, StockCandle> stockCacheService,
                            MarketConstants marketConstants,
                            @Lazy StockDataService self) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.marketConstants = marketConstants;
        this.self = self;
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
                Stock stock = self.updateSingleStockSnapshot(symbol);
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
    @Transactional
    public Stock updateSingleStockSnapshot(String symbol) {
        YahooStockQuoteDto dto = yahooStockClient.fetchSnapshot(symbol);
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
                int candleCount = self.updateCandlesForStock(symbol);
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
    @Transactional
    public int updateCandlesForStock(String symbol) {
        Stock stock = stockRepository.getReferenceById(symbol);
        long existingCount = stockCandleRepository.countByStockSymbol(symbol);
        String range = existingCount < MIN_CANDLES_FOR_INCREMENTAL ? "5y" : "5d";
        log.debug("{} - existing: {}, range: {}", symbol, existingCount, range);
        List<YahooCandleDto> candleDtos = yahooStockClient.fetchCandles(symbol, range, "1d");
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
        if ("5y".equals(range)) {
            LocalDateTime fiveYearsAgo = LocalDateTime.now(ISTANBUL_ZONE).minusYears(5);
            stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, fiveYearsAgo);
        }
        return newCount + updateCount;
    }
}
