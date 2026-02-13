package com.finance.backend.service;

import com.finance.backend.model.Forex;
import com.finance.backend.model.ForexCandle;
import com.finance.backend.repository.ForexRepository;
import com.finance.backend.repository.ForexCandleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooForexService {
    
    private final ForexRepository forexRepository;
    private final ForexCandleRepository forexCandleRepository;
    private final ForexCacheService forexCacheService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final int YEARS_TO_KEEP = 5;
    private static final int MIN_CANDLES_FOR_INCREMENTAL = 1200;
    private static final BigDecimal SPREAD_RATE = new BigDecimal("0.01");
    
    @PostConstruct
    public void init() {
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            return execution.execute(request, body);
        });
        log.info("YahooForexService initialized with User-Agent header");
    }
    
    @Transactional
    public void syncAllYahooSnapshots() {
        log.info("Starting Yahoo Finance SNAPSHOT-ONLY sync...");
        
        List<Forex> allForex = forexRepository.findAll();
        
        for (Forex forex : allForex) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiting interrupted");
            }
            
            updateForexSnapshot(forex);
        }
        
        forexCacheService.clearAllSnapshotCache();
        log.info("Completed Yahoo Finance SNAPSHOT-ONLY sync");
    }
    
    @Transactional
    public void syncAllYahooCandles() {
        log.info("Starting Yahoo Finance CANDLES-ONLY sync...");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(YEARS_TO_KEEP);
        forexCandleRepository.deleteByCandleDateBefore(cutoffDate);
        
        List<Forex> allForex = forexRepository.findAll();
        
        Forex usdtry = allForex.stream()
            .filter(f -> "USDTRY".equals(f.getCurrencyCode()))
            .findFirst()
            .orElse(null);
        
        if (usdtry == null) {
            log.error("USDTRY not found in database");
            return;
        }
        
        updateForexCandles(usdtry);
        
        for (Forex forex : allForex) {
            if ("USDTRY".equals(forex.getCurrencyCode())) {
                continue;
            }
            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiting interrupted");
            }
            
            updateForexCandles(forex);
        }
        
        forexCacheService.clearAllHistoryCache();
        log.info("Completed Yahoo Finance CANDLES-ONLY sync");
    }
    
    
    @Transactional
    public void updateForexSnapshot(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        
        try {
            String url = YAHOO_CHART_URL + yahooSymbol + "?range=1d&interval=1m";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result").get(0);
            
            if (result != null && !result.isMissingNode()) {
                JsonNode meta = result.path("meta");
                BigDecimal currentPrice = getDecimal(meta.path("regularMarketPrice"));
                BigDecimal previousClose = getDecimal(meta.path("previousClose"));
                
                if (currentPrice != null) {
                    BigDecimal sellingPrice = currentPrice.multiply(BigDecimal.ONE.add(SPREAD_RATE));
                    
                    forex.setCurrentPrice(currentPrice.setScale(4, RoundingMode.HALF_UP));
                    forex.setSellingPrice(sellingPrice.setScale(4, RoundingMode.HALF_UP));
                    
                    if (previousClose != null) {
                        BigDecimal change = currentPrice.subtract(previousClose);
                        BigDecimal changePercent = change.divide(previousClose, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
                        forex.setChange24h(change.setScale(4, RoundingMode.HALF_UP));
                        forex.setChangePercent24h(changePercent.setScale(4, RoundingMode.HALF_UP));
                    }
                    
                    forex.setUpdatedAt(LocalDateTime.now());
                    forex.setYahooUpdatedAt(LocalDateTime.now());
                    forexRepository.save(forex);
                    log.info("[SNAPSHOT] Updated {} price: {}", forex.getCurrencyCode(), currentPrice);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[SNAPSHOT] Direct fetch failed for {}: {}", yahooSymbol, e.getMessage());
        }
        
        if (!"USDTRY".equals(baseSymbol)) {
            Forex usdtry = forexRepository.findByCurrencyCode("USDTRY").orElse(null);
            if (usdtry != null && usdtry.getCurrentPrice() != null) {
                String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
                String[] attempts = {
                    baseCurrency + "USD=X",
                    "USD" + baseCurrency + "=X"
                };
                
                for (String symbol : attempts) {
                    try {
                        String url = YAHOO_CHART_URL + symbol + "?range=1d&interval=1m";
                        String response = restTemplate.getForObject(url, String.class);
                        JsonNode root = objectMapper.readTree(response);
                        JsonNode result = root.path("chart").path("result").get(0);
                        
                        if (result != null && !result.isMissingNode()) {
                            JsonNode meta = result.path("meta");
                            BigDecimal pairPrice = getDecimal(meta.path("regularMarketPrice"));
                            BigDecimal pairPreviousClose = getDecimal(meta.path("previousClose"));
                            
                            if (pairPrice != null) {
                                boolean isUsdBase = symbol.startsWith("USD");
                                BigDecimal syntheticPrice = isUsdBase 
                                    ? usdtry.getCurrentPrice().divide(pairPrice, 4, RoundingMode.HALF_UP)
                                    : usdtry.getCurrentPrice().multiply(pairPrice);
                                
                                BigDecimal sellingPrice = syntheticPrice.multiply(BigDecimal.ONE.add(SPREAD_RATE));
                                
                                forex.setCurrentPrice(syntheticPrice.setScale(4, RoundingMode.HALF_UP));
                                forex.setSellingPrice(sellingPrice.setScale(4, RoundingMode.HALF_UP));
                                
                                if (pairPreviousClose != null && usdtry.getChange24h() != null) {
                                    BigDecimal usdtryPreviousClose = usdtry.getCurrentPrice().subtract(usdtry.getChange24h());
                                    BigDecimal syntheticPreviousClose = isUsdBase
                                        ? usdtryPreviousClose.divide(pairPreviousClose, 4, RoundingMode.HALF_UP)
                                        : usdtryPreviousClose.multiply(pairPreviousClose);
                                    
                                    BigDecimal change = syntheticPrice.subtract(syntheticPreviousClose);
                                    BigDecimal changePercent = change.divide(syntheticPreviousClose, 4, RoundingMode.HALF_UP)
                                            .multiply(new BigDecimal("100"));
                                    forex.setChange24h(change.setScale(4, RoundingMode.HALF_UP));
                                    forex.setChangePercent24h(changePercent.setScale(4, RoundingMode.HALF_UP));
                                } else if (pairPreviousClose != null) {
                                    BigDecimal syntheticPreviousClose = isUsdBase
                                        ? usdtry.getCurrentPrice().divide(pairPreviousClose, 4, RoundingMode.HALF_UP)
                                        : usdtry.getCurrentPrice().multiply(pairPreviousClose);
                                    
                                    BigDecimal change = syntheticPrice.subtract(syntheticPreviousClose);
                                    BigDecimal changePercent = change.divide(syntheticPreviousClose, 4, RoundingMode.HALF_UP)
                                            .multiply(new BigDecimal("100"));
                                    forex.setChange24h(change.setScale(4, RoundingMode.HALF_UP));
                                    forex.setChangePercent24h(changePercent.setScale(4, RoundingMode.HALF_UP));
                                }
                                
                                forex.setUpdatedAt(LocalDateTime.now());
                                forex.setYahooUpdatedAt(LocalDateTime.now());
                                forexRepository.save(forex);
                                log.info("[SNAPSHOT-SYNTHETIC] Updated {} price: {} change: {}", 
                                    forex.getCurrencyCode(), syntheticPrice, forex.getChangePercent24h());
                                return;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[SNAPSHOT-SYNTHETIC] Failed for {}: {}", symbol, e.getMessage());
                    }
                }
            }
        }
        
        log.error("[SNAPSHOT] All attempts failed for {}", baseSymbol);
    }
    
    @Transactional
    public void updateForexCandles(Forex forex) {
        String baseSymbol = forex.getCurrencyCode();
        String yahooSymbol = baseSymbol + "=X";
        
        long candleCount = forexCandleRepository.countByCurrencyCode(baseSymbol);
        String range = candleCount >= MIN_CANDLES_FOR_INCREMENTAL ? "1d" : "5y";
        String interval = "1d".equals(range) ? "1m" : "1d";
        
        try {
            String url = YAHOO_CHART_URL + yahooSymbol + "?range=" + range + "&interval=" + interval;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result").get(0);
            
            if (result != null && !result.isMissingNode()) {
                int candlesSaved = saveForexCandles(forex, result, false, null, null);
                
                if (candlesSaved >= 100 || "1d".equals(range)) {
                    log.info("[CANDLES-DIRECT] ✅ Saved {} candles for {}", candlesSaved, baseSymbol);
                    return;
                }
                
                if ("5y".equals(range)) {
                    log.warn("[CANDLES-DIRECT] Only {} candles, trying synthetic", candlesSaved);
                }
            }
        } catch (Exception e) {
            log.warn("[CANDLES-DIRECT] Fetch failed for {}: {}", yahooSymbol, e.getMessage());
        }
        
        if (!"USDTRY".equals(baseSymbol)) {
            Forex usdtry = forexRepository.findByCurrencyCode("USDTRY").orElse(null);
            if (usdtry == null) {
                log.error("[CANDLES-SYNTHETIC] USDTRY not available for {}", baseSymbol);
                return;
            }
            
            List<ForexCandle> usdtryCandles = forexCandleRepository.findTop1825ByCurrencyCodeOrderByCandleDateDesc("USDTRY");
            if (usdtryCandles.isEmpty()) {
                log.error("[CANDLES-SYNTHETIC] USDTRY candles not available for {}", baseSymbol);
                return;
            }
            
            String baseCurrency = forex.getCurrencyCode().replace("TRY", "");
            String[] attempts = {
                baseCurrency + "USD=X",
                "USD" + baseCurrency + "=X"
            };
            
            for (String symbol : attempts) {
                try {
                    String url = YAHOO_CHART_URL + symbol + "?range=5y&interval=1d";
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode root = objectMapper.readTree(response);
                    JsonNode result = root.path("chart").path("result").get(0);
                    
                    if (result != null && !result.isMissingNode()) {
                        boolean isUsdBase = symbol.startsWith("USD");
                        int candlesSaved = saveForexCandles(forex, result, true, usdtryCandles, isUsdBase);
                        log.info("[CANDLES-SYNTHETIC] ✅ Saved {} candles for {}", candlesSaved, baseSymbol);
                        return;
                    }
                } catch (Exception e) {
                    log.debug("[CANDLES-SYNTHETIC] Failed for {}: {}", symbol, e.getMessage());
                }
            }
        }
        
        log.error("[CANDLES] All attempts failed for {}", baseSymbol);
    }

    
    private int saveForexCandles(Forex forex, JsonNode result, boolean isSynthetic, 
                                  List<ForexCandle> usdtryCandles, Boolean isUsdBase) {
        try {
            JsonNode timestamps = result.path("timestamp");
            JsonNode quotes = result.path("indicators").path("quote").get(0);
            
            String mode = isSynthetic ? "SYNTHETIC" : "DIRECT";
            log.info("[CANDLES-{}] Processing for {}: timestamps={}, quotes={}", 
                mode,
                forex.getCurrencyCode(), 
                timestamps.isMissingNode() ? "MISSING" : timestamps.size() + " items",
                (quotes == null || quotes.isMissingNode()) ? "MISSING" : "OK");
            
            if (timestamps.isMissingNode() || quotes == null || quotes.isMissingNode()) {
                log.warn("[CANDLES-{}] No valid data for {}", mode, forex.getCurrencyCode());
                return 0;
            }
            
            JsonNode opens = quotes.path("open");
            JsonNode highs = quotes.path("high");
            JsonNode lows = quotes.path("low");
            JsonNode closes = quotes.path("close");
            
            List<ForexCandle> candlesToSave = new ArrayList<>();
            BigDecimal usdtryCurrentPrice = forex.getCurrentPrice();
            
            for (int i = 0; i < timestamps.size(); i++) {
                long timestamp = timestamps.get(i).asLong();
                LocalDateTime candleDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
                
                BigDecimal open = getDecimal(opens.get(i));
                BigDecimal high = getDecimal(highs.get(i));
                BigDecimal low = getDecimal(lows.get(i));
                BigDecimal close = getDecimal(closes.get(i));
                
                if (open == null || high == null || low == null || close == null) {
                    continue;
                }
                
                if (isSynthetic && usdtryCandles != null && isUsdBase != null) {
                    BigDecimal usdtryPrice = usdtryCandles.stream()
                        .filter(c -> c.getCandleDate().toLocalDate().equals(candleDate.toLocalDate()))
                        .map(ForexCandle::getClose)
                        .findFirst()
                        .orElse(usdtryCurrentPrice);
                    
                    if (isUsdBase) {
                        open = usdtryPrice.divide(open, 4, RoundingMode.HALF_UP);
                        high = usdtryPrice.divide(low, 4, RoundingMode.HALF_UP);  // Inverse
                        low = usdtryPrice.divide(high, 4, RoundingMode.HALF_UP);
                        close = usdtryPrice.divide(close, 4, RoundingMode.HALF_UP);
                    } else {
                        open = usdtryPrice.multiply(open);
                        high = usdtryPrice.multiply(high);
                        low = usdtryPrice.multiply(low);
                        close = usdtryPrice.multiply(close);
                    }
                }
                
                open = open.setScale(4, RoundingMode.HALF_UP);
                high = high.setScale(4, RoundingMode.HALF_UP);
                low = low.setScale(4, RoundingMode.HALF_UP);
                close = close.setScale(4, RoundingMode.HALF_UP);
                
                Optional<ForexCandle> existingCandle = forexCandleRepository.findByCurrencyCodeAndCandleDate(
                    forex.getCurrencyCode(), candleDate);
                
                ForexCandle candle;
                if (existingCandle.isPresent()) {
                    candle = existingCandle.get();
                    candle.setOpen(open);
                    candle.setHigh(high);
                    candle.setLow(low);
                    candle.setClose(close);
                    candle.setUpdatedAt(LocalDateTime.now());
                } else {
                    candle = new ForexCandle();
                    candle.setCurrencyCode(forex.getCurrencyCode());
                    candle.setForex(forex);
                    candle.setCandleDate(candleDate);
                    candle.setOpen(open);
                    candle.setHigh(high);
                    candle.setLow(low);
                    candle.setClose(close);
                    candle.setCreatedAt(LocalDateTime.now());
                    candle.setUpdatedAt(LocalDateTime.now());
                }
                
                candlesToSave.add(candle);
            }
            
            if (!candlesToSave.isEmpty()) {
                forexCandleRepository.saveAll(candlesToSave);
                log.info("[CANDLES-{}]  Saved {} candles for {}", mode, candlesToSave.size(), forex.getCurrencyCode());
                return candlesToSave.size();
            } else {
                log.warn("[CANDLES-{}]  No valid candles to save for {}", mode, forex.getCurrencyCode());
                return 0;
            }
            
        } catch (Exception e) {
            log.error("[CANDLES] Failed to save for {}: {}", forex.getCurrencyCode(), e.getMessage(), e);
            return 0;
        }
    }
    
    private BigDecimal getDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
