package com.finance.backend.service;
import com.finance.backend.client.YahooStockClient;
import com.finance.backend.dto.external.YahooCandleDto;
import com.finance.backend.dto.external.YahooStockQuoteDto;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.StockMapper;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.repository.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j
@Service
public class StockDataService {
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final double FAILURE_THRESHOLD = 0.5;
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1200;
    private final YahooStockClient yahooStockClient;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockCacheService stockCacheService;
    private final MarketConstants marketConstants;
    public StockDataService(YahooStockClient yahooStockClient,
                            StockMapper stockMapper,
                            StockRepository stockRepository,
                            StockCandleRepository stockCandleRepository,
                            StockCacheService stockCacheService,
                            MarketConstants marketConstants) {
        this.yahooStockClient = yahooStockClient;
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
        this.stockCandleRepository = stockCandleRepository;
        this.stockCacheService = stockCacheService;
        this.marketConstants = marketConstants;
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
                updateSingleStockSnapshot(symbol);
                stockCacheService.clearSnapshotCache(symbol);
                successCount++;
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update snapshot for {}: {}", symbol, e.getMessage());
                checkFailureThreshold(successCount, failCount, failedSymbols, "snapshot");
            }
        }
        log.info("Snapshot update completed: {} success, {} failed", successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
    }
    public void updateSingleStockSnapshot(String symbol) {
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
        log.info("Updated snapshot: {} - TRY {}", symbol, stock.getCurrentPrice());
    }
    public void updateStockCandles() {
        List<String> bistStocks = marketConstants.getTrackedBistStocks();
        if (bistStocks.isEmpty()) {
            log.warn("No BIST stocks configured in environment variables");
            return;
        }
        log.info("Starting candle update for {} BIST stocks (5 years)", bistStocks.size());
        int totalCandles = 0;
        int successCount = 0;
        int failCount = 0;
        List<String> failedSymbols = new ArrayList<>();
        for (String symbol : bistStocks) {
            try {
                int candleCount = updateCandlesForStock(symbol);
                stockCacheService.clearHistoryCache(symbol);
                totalCandles += candleCount;
                successCount++;
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update candles for {} (transaction rolled back): {}", symbol, e.getMessage());
                checkFailureThreshold(successCount, failCount, failedSymbols, "candle");
            }
        }
        log.info("Candle update completed: {} total candles, {} success, {} failed", totalCandles, successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
    }
    public int updateCandlesForStock(String symbol) {
        Stock stock = stockRepository.getReferenceById(symbol);
        long existingCount = stockCandleRepository.countByStockSymbol(symbol);
        String range = existingCount < MIN_CANDLES_FOR_INCREMENTAL ? "5y" : "5d";
        log.info("{} - Existing candles: {}, using range: {}", symbol, existingCount, range);
        List<YahooCandleDto> candleDtos = yahooStockClient.fetchCandles(symbol, range, "1d");
        if (candleDtos.isEmpty()) {
            log.warn("{} - No valid candle data", symbol);
            return 0;
        }
        Map<LocalDateTime, StockCandle> existingMap = stockCandleRepository
                .findByStockSymbolOrderByCandleDateDesc(symbol)
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
                toSave.add(existing);
                updateCount++;
            } else {
                toSave.add(stockMapper.toCandleEntity(dto, stock));
                newCount++;
            }
        }
        if (!toSave.isEmpty()) {
            stockCandleRepository.saveAll(toSave);
            log.info("{} - Saved {} candles ({} new, {} updated)", symbol, toSave.size(), newCount, updateCount);
        }
        if ("5y".equals(range)) {
            LocalDateTime fiveYearsAgo = LocalDateTime.now(ISTANBUL_ZONE).minusYears(5);
            stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, fiveYearsAgo);
        }
        return toSave.size();
    }
    private void checkFailureThreshold(int successCount, int failCount,
                                       List<String> failedSymbols, String type) {
        double failureRate = (double) failCount / (successCount + failCount);
        if (failureRate > FAILURE_THRESHOLD && (successCount + failCount) >= 10) {
            log.error("CRITICAL API ERROR: Failure rate {}% exceeded threshold during {} update. Failed symbols: {}",
                    String.format("%.1f", failureRate * 100), type, failedSymbols);
            throw new BusinessException(
                    String.format("Critical API failure: %d out of %d stocks failed (%.1f%%)",
                            failCount, successCount + failCount, failureRate * 100),
                    "CRITICAL_API_FAILURE");
        }
    }
}
