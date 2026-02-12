package com.finance.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.constants.MarketConstants;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.repository.StockCandleRepository;
import com.finance.backend.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataService {

    private final StockRepository stockRepository;
    private final StockCandleRepository stockCandleRepository;
    private final StockCacheService stockCacheService;
    private final MarketConstants marketConstants;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String YAHOO_FINANCE_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final double FAILURE_THRESHOLD = 0.5;

    @PostConstruct
    public void init() {
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            return execution.execute(request, body);
        });
        log.info("StockDataService initialized with User-Agent header");
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
                successCount++;
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate limiting interrupted");
                }
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update snapshot for {}: {}", symbol, e.getMessage());
                
                double failureRate = (double) failCount / (successCount + failCount);
                if (failureRate > FAILURE_THRESHOLD && (successCount + failCount) >= 10) {
                    log.error("CRITICAL API ERROR: Failure rate {}% exceeded threshold. Stopping update. Failed symbols: {}",
                        String.format("%.1f", failureRate * 100), failedSymbols);
                    throw new BusinessException(
                        String.format("Critical API failure: %d out of %d stocks failed (%.1f%%)",
                            failCount, successCount + failCount, failureRate * 100),
                        "CRITICAL_API_FAILURE"
                    );
                }
            }
        }

        log.info("Snapshot update completed: {} success, {} failed", successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
        
        stockCacheService.clearSnapshotCache();
    }
    
    @Transactional
    public void updateSingleStockSnapshot(String symbol) {
        Stock stock = fetchStockSnapshot(symbol);
        if (stock == null) {
            throw new BusinessException(
                "Failed to fetch stock data from external API: " + symbol,
                "EXTERNAL_API_ERROR"
            );
        }
        if (stock.getCurrentPrice() == null) {
            throw new BusinessException(
                "Invalid stock data received - missing price for: " + symbol,
                "INVALID_EXTERNAL_DATA"
            );
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
                int candleCount = updateCandlesForStockTransactional(symbol);
                totalCandles += candleCount;
                successCount++;
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Rate limiting interrupted");
                }
            } catch (Exception e) {
                failCount++;
                failedSymbols.add(symbol);
                log.error("Failed to update candles for {} (transaction rolled back): {}", symbol, e.getMessage());
                
                double failureRate = (double) failCount / (successCount + failCount);
                if (failureRate > FAILURE_THRESHOLD && (successCount + failCount) >= 10) {
                    log.error("CRITICAL API ERROR: Failure rate {}% exceeded threshold. Stopping candle update. Failed symbols: {}",
                        String.format("%.1f", failureRate * 100), failedSymbols);
                    throw new BusinessException(
                        String.format("Critical API failure during candle update: %d out of %d stocks failed (%.1f%%)",
                            failCount, successCount + failCount, failureRate * 100),
                        "CRITICAL_API_FAILURE"
                    );
                }
            }
        }

        log.info("Candle update completed: {} total candles, {} success, {} failed", totalCandles, successCount, failCount);
        if (!failedSymbols.isEmpty()) {
            log.warn("Failed symbols: {}", failedSymbols);
        }
        
        stockCacheService.clearHistoryCache();
    }
    
    @Transactional
    public int updateCandlesForStockTransactional(String symbol) {
        long existingCount = stockCandleRepository.countByStockSymbol(symbol);
        String range = existingCount < 1200 ? "5y" : "5d";
        
        log.info("{} - Existing candles: {}, using range: {}", symbol, existingCount, range);
        return updateCandlesForStock(symbol, range);
    }

    private Stock fetchStockSnapshot(String symbol) {
        try {
            String url = YAHOO_FINANCE_BASE_URL + symbol + "?range=1d&interval=1d";
            log.info("Fetching stock snapshot for {} from: {}", symbol, url);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();
            
            if (response == null) {
                log.warn("Empty response for {}", symbol);
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result").get(0);
            
            if (result == null || result.isMissingNode()) {
                log.warn("No result data for {}", symbol);
                return null;
            }

            JsonNode meta = result.path("meta");
            JsonNode quote = result.path("indicators").path("quote").get(0);

            BigDecimal currentPrice = getBigDecimal(meta.path("regularMarketPrice"));
            BigDecimal previousClose = getBigDecimal(meta.path("chartPreviousClose"));
            BigDecimal dayHigh = getBigDecimal(meta.path("regularMarketDayHigh"));
            BigDecimal dayLow = getBigDecimal(meta.path("regularMarketDayLow"));
            long volume = meta.path("regularMarketVolume").asLong(0);
            
            BigDecimal openPrice = null;
            if (quote != null && quote.has("open") && quote.path("open").isArray() && quote.path("open").size() > 0) {
                openPrice = getBigDecimal(quote.path("open").get(0));
            }

            BigDecimal priceChangeAmount = null;
            BigDecimal priceChangePercent = null;
            
            if (currentPrice != null && previousClose != null && previousClose.compareTo(BigDecimal.ZERO) > 0) {
                priceChangeAmount = currentPrice.subtract(previousClose);
                priceChangePercent = priceChangeAmount
                    .divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            }

            return Stock.builder()
                    .symbol(symbol)
                    .name(meta.path("longName").asText(meta.path("shortName").asText(symbol)))
                    .currentPrice(currentPrice)
                    .previousClose(previousClose)
                    .openPrice(openPrice)
                    .dayHigh(dayHigh)
                    .dayLow(dayLow)
                    .volume(volume)
                    .priceChangeAmount(priceChangeAmount)
                    .priceChangePercent(priceChangePercent)
                    .exchange(meta.path("exchangeName").asText("BIST"))
                    .currency(meta.path("currency").asText("TRY"))
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error fetching snapshot for {}: {}", symbol, e.getMessage());
            throw new BusinessException(
                "Failed to fetch stock data from Yahoo Finance: " + symbol,
                "EXTERNAL_API_ERROR"
            );
        }
    }

    private int updateCandlesForStock(String symbol, String range) {
        try {
            String url = YAHOO_FINANCE_BASE_URL + symbol + "?range=" + range + "&interval=1d";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String response = responseEntity.getBody();
            
            if (response == null) {
                log.warn("{} - Empty API response", symbol);
                return 0;
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result").get(0);
            
            if (result == null || result.isMissingNode()) {
                log.warn("{} - No result data", symbol);
                return 0;
            }

            JsonNode timestamps = result.path("timestamp");
            if (!timestamps.isArray() || timestamps.size() == 0) {
                log.warn("{} - No timestamp data", symbol);
                return 0;
            }

            JsonNode quote = result.path("indicators").path("quote").get(0);
            if (quote == null) {
                log.warn("{} - No quote data", symbol);
                return 0;
            }

            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            Map<LocalDateTime, CandleData> apiCandles = new HashMap<>();
            
            for (int i = 0; i < timestamps.size(); i++) {
                if (isNullNode(opens, i) || isNullNode(highs, i) || 
                    isNullNode(lows, i) || isNullNode(closes, i)) {
                    continue;
                }

                long unixTimestamp = timestamps.get(i).asLong();
                LocalDateTime candleDate = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(unixTimestamp), 
                    ISTANBUL_ZONE
                ).truncatedTo(java.time.temporal.ChronoUnit.DAYS);

                CandleData candle = new CandleData(
                    getBigDecimal(opens.get(i)),
                    getBigDecimal(highs.get(i)),
                    getBigDecimal(lows.get(i)),
                    getBigDecimal(closes.get(i)),
                    volumes.get(i).asLong(0)
                );
                
                apiCandles.putIfAbsent(candleDate, candle);
            }

            if (apiCandles.isEmpty()) {
                log.warn("{} - No valid candle data", symbol);
                return 0;
            }

            List<StockCandle> existingCandles = stockCandleRepository
                .findByStockSymbolOrderByCandleDateDesc(symbol);
            
            Map<LocalDateTime, StockCandle> existingMap = existingCandles.stream()
                .collect(Collectors.toMap(
                    candle -> candle.getCandleDate().truncatedTo(java.time.temporal.ChronoUnit.DAYS),
                    candle -> candle,
                    (existing, replacement) -> existing
                ));

            List<StockCandle> toSave = new ArrayList<>();
            int newCount = 0;
            int updateCount = 0;

            for (Map.Entry<LocalDateTime, CandleData> entry : apiCandles.entrySet()) {
                LocalDateTime candleDate = entry.getKey();
                CandleData data = entry.getValue();
                
                StockCandle existingCandle = existingMap.get(candleDate);
                
                if (existingCandle != null) {
                    existingCandle.setOpen(data.open);
                    existingCandle.setHigh(data.high);
                    existingCandle.setLow(data.low);
                    existingCandle.setClose(data.close);
                    existingCandle.setVolume(data.volume);
                    toSave.add(existingCandle);
                    updateCount++;
                } else {
                    StockCandle newCandle = StockCandle.builder()
                        .stockSymbol(symbol)
                        .candleDate(candleDate)
                        .open(data.open)
                        .high(data.high)
                        .low(data.low)
                        .close(data.close)
                        .volume(data.volume)
                        .build();
                    
                    toSave.add(newCandle);
                    newCount++;
                }
            }

            if (!toSave.isEmpty()) {
                stockCandleRepository.saveAll(toSave);
                log.info("{} - Saved {} candles ({} new, {} updated)", 
                    symbol, toSave.size(), newCount, updateCount);
            }
            
            if ("5y".equals(range)) {
                LocalDateTime fiveYearsAgo = LocalDateTime.now(ISTANBUL_ZONE).minusYears(5);
                stockCandleRepository.deleteByStockSymbolAndCandleDateBefore(symbol, fiveYearsAgo);
            }

            return toSave.size();

        } catch (Exception e) {
            log.error("{} - Candle update failed: {}", symbol, e.getMessage());
            throw new BusinessException(
                "Failed to update candles for " + symbol + ": " + e.getMessage(),
                "CANDLE_UPDATE_ERROR"
            );
        }
    }

    private boolean isNullNode(JsonNode array, int index) {
        if (!array.isArray() || index >= array.size()) {
            return true;
        }
        JsonNode node = array.get(index);
        return node == null || node.isNull();
    }

    private BigDecimal getBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        
        if (node.isNumber()) {
            double value = node.asDouble();
            if (!Double.isFinite(value)) {
                return null;
            }
            return BigDecimal.valueOf(value);
        }
        
        return null;
    }
    private static class CandleData {
        public final BigDecimal open;
        public final BigDecimal high;
        public final BigDecimal low;
        public final BigDecimal close;
        public final long volume;

        CandleData(BigDecimal open, BigDecimal high, 
                   BigDecimal low, BigDecimal close, long volume) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}
