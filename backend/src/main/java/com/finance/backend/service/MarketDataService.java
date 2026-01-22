package com.finance.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.model.Crypto;
import com.finance.backend.model.CryptoCandle;
import com.finance.backend.repository.CryptoCandleRepository;
import com.finance.backend.repository.CryptoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Market Data Service - Modular Engine for Real Exchange Data
 * Handles CoinGecko API integration with rate limiting
 * 
 * Features:
 * - updateOnlySnapshots(): Fetch current prices
 * - updateOnlyCandles(): Fetch OHLC data with rate limiting
 * - fullMarketUpdate(): Combined update
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {
    
    private final CryptoRepository cryptoRepository;
    private final CryptoCandleRepository cryptoCandleRepository;
    private final CryptoCacheService cryptoCacheService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${coingecko.api.key}")
    private String apiKey;
    
    private static final String COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3";
    
    // Top 12 cryptocurrencies to track
    private static final List<String> TRACKED_COINS = List.of(
        "bitcoin", "ethereum", "tether", "binancecoin", "solana", "ripple",
        "usd-coin", "cardano", "avalanche-2", "dogecoin", "tron", "polkadot"
    );
    
    /**
     * Update ONLY current price snapshots
     * Endpoint: /coins/markets
     * Independent operation - can be called separately
     */
    public void updateOnlySnapshots() {
        try {
            log.info("📊 Starting snapshot update for {} coins...", TRACKED_COINS.size());
            
            // Fetch USD prices
            String urlUsd = COINGECKO_BASE_URL + "/coins/markets" +
                    "?vs_currency=usd" +
                    "&ids=" + String.join(",", TRACKED_COINS) +
                    "&order=market_cap_desc" +
                    "&sparkline=false";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-cg-demo-api-key", apiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> responseUsd = restTemplate.exchange(urlUsd, HttpMethod.GET, entity, String.class);
            JsonNode marketsUsd = objectMapper.readTree(responseUsd.getBody());
            
            // Fetch TRY prices
            String urlTry = COINGECKO_BASE_URL + "/coins/markets" +
                    "?vs_currency=try" +
                    "&ids=" + String.join(",", TRACKED_COINS) +
                    "&order=market_cap_desc" +
                    "&sparkline=false";
            
            ResponseEntity<String> responseTry = restTemplate.exchange(urlTry, HttpMethod.GET, entity, String.class);
            JsonNode marketsTry = objectMapper.readTree(responseTry.getBody());
            
            List<Crypto> cryptos = new ArrayList<>();
            
            // Create a map for TRY prices
            var tryPriceMap = new java.util.HashMap<String, BigDecimal>();
            for (JsonNode market : marketsTry) {
                tryPriceMap.put(
                    market.get("id").asText(), 
                    BigDecimal.valueOf(market.get("current_price").asDouble())
                );
            }
            
            for (JsonNode market : marketsUsd) {
                String coinId = market.get("id").asText();
                Crypto crypto = Crypto.builder()
                        .id(coinId)
                        .symbol(market.get("symbol").asText().toUpperCase())
                        .name(market.get("name").asText())
                        .image(market.get("image").asText())
                        .currentPrice(BigDecimal.valueOf(market.get("current_price").asDouble()))
                        .currentPriceTry(tryPriceMap.getOrDefault(coinId, BigDecimal.ZERO))
                        .changeAmount(BigDecimal.valueOf(market.get("price_change_24h").asDouble()))
                        .changePercent(BigDecimal.valueOf(market.get("price_change_percentage_24h").asDouble()))
                        .exchange("CoinGecko")
                        .currency("USD")
                        .lastUpdated(LocalDateTime.now())
                        .build();
                
                cryptos.add(crypto);
            }
            
            cryptoRepository.saveAll(cryptos);
            cryptoCacheService.clearAllCache();
            
            log.info("✅ Snapshot update completed: {} coins saved (USD + TRY)", cryptos.size());
            
        } catch (Exception e) {
            log.error("❌ Snapshot update failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update snapshots", e);
        }
    }
    
    /**
     * FIX: Uses /market_chart/range with UNIX timestamps to force daily granularity.
     * CoinGecko automatically compresses "days=365" requests, but "range" requests
     * tend to respect the daily interval better.
     */
    private List<CryptoCandle> fetchHistoryFromMarketChart(String coinId) throws Exception {
        log.info("  📊 Fetching 365-day history from market_chart/range for {}", coinId);
        
        // 1. Hesaplama: UNIX Zaman Damgaları (Seconds)
        long toTimestamp = Instant.now().getEpochSecond();
        long fromTimestamp = Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS).getEpochSecond();

        // 2. URL: Range Endpoint Kullanımı
        String url = COINGECKO_BASE_URL + "/coins/" + coinId + "/market_chart/range" +
                "?vs_currency=usd" +
                "&from=" + fromTimestamp +
                "&to=" + toTimestamp;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cg-demo-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode jsonData = objectMapper.readTree(response.getBody());
        JsonNode pricesArray = jsonData.get("prices");
        
        if (!pricesArray.isArray() || pricesArray.size() == 0) {
            log.warn("  ⚠️ No price data returned for {}", coinId);
            return new ArrayList<>();
        }
        
        List<CryptoCandle> candles = new ArrayList<>();
        BigDecimal previousClose = null;
        
        for (JsonNode pricePoint : pricesArray) {
            long timestamp = pricePoint.get(0).asLong();
            BigDecimal price = BigDecimal.valueOf(pricePoint.get(1).asDouble());
            
            LocalDateTime candleDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault() // Veya ZoneId.of("UTC")
            );
            
            // Veri kalabalığını önlemek için saatlik veri gelirse sadece 00:00'ı alabiliriz
            // Ama market_chart/range 90 günden uzunsa zaten otomatik günlük verir.
            
            BigDecimal open, high, low, close;
            
            if (previousClose == null) {
                open = high = low = close = price;
            } else {
                open = previousClose;
                close = price;
                high = open.max(close);
                low = open.min(close);
            }
            
            CryptoCandle candle = CryptoCandle.builder()
                    .cryptoId(coinId)
                    .candleDate(candleDate)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .build();
            
            candles.add(candle);
            previousClose = close;
        }
        
        // KONTROL: Eğer hala az veri geliyorsa logla
        if(candles.size() < 300) {
             log.warn("⚠️ CoinGecko hala sıkıştırılmış veri gönderiyor olabilir: {} adet veri geldi.", candles.size());
        } else {
             log.info("  ✓ Successfully fetched {} daily candles for {}", candles.size(), coinId);
        }

        return candles;
    }
    
    /**
     * Fetch REAL OHLC data for today's candle
     * Endpoint: /coins/{id}/ohlc?vs_currency=usd&days=1
     */
    private List<CryptoCandle> fetchDailyFromOHLC(String coinId) throws Exception {
        log.info("  📈 Fetching today's OHLC for {}", coinId);
        
        String url = COINGECKO_BASE_URL + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=1";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cg-demo-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode ohlcData = objectMapper.readTree(response.getBody());
        
        if (!ohlcData.isArray() || ohlcData.size() == 0) {
            log.warn("  ⚠️ No OHLC data returned for {}", coinId);
            return new ArrayList<>();
        }
        
        List<CryptoCandle> candles = new ArrayList<>();
        
        // Get the latest candle (last element in array)
        JsonNode latestCandle = ohlcData.get(ohlcData.size() - 1);
        
        long timestamp = latestCandle.get(0).asLong();
        LocalDateTime candleDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );
        
        CryptoCandle candle = CryptoCandle.builder()
                .cryptoId(coinId)
                .candleDate(candleDate)
                .open(BigDecimal.valueOf(latestCandle.get(1).asDouble()))
                .high(BigDecimal.valueOf(latestCandle.get(2).asDouble()))
                .low(BigDecimal.valueOf(latestCandle.get(3).asDouble()))
                .close(BigDecimal.valueOf(latestCandle.get(4).asDouble()))
                .build();
        
        candles.add(candle);
        log.info("  ✓ Fetched real OHLC for {} (Close: ${})", coinId, candle.getClose());
        return candles;
    }
    
    /**
     * Update ONLY candle (OHLC) data - HYBRID STRATEGY with SELF-HEALING
     * 
     * Logic:
     * - Check candle count for each coin
     * - If count < 350 (data gaps detected): Wipe and reload full 365-day history
     * - If count >= 350 (data healthy): Fetch only today's real OHLC
     * - After all fetches: Prune data older than 365 days
     * 
     * Self-Healing: Automatically fixes gaps from incorrect initial fetches
     * Rate limited: 3 second delay between requests
     */
    public void updateOnlyCandles() {
        try {
            log.info("📈 Starting HYBRID candle update with SELF-HEALING for {} coins...", TRACKED_COINS.size());
            
            int processed = 0;
            int totalCandles = 0;
            
            for (String coinId : TRACKED_COINS) {
                try {
                    // Rate limit: 3 second delay between requests
                    if (processed > 0) {
                        Thread.sleep(3000);
                    }
                    
                    // SELF-HEALING CHECK: Count existing candles
                    long count = cryptoCandleRepository.countByCryptoId(coinId);
                    
                    List<CryptoCandle> candles;
                    
                    if (count < 350) {
                        // DATA GAP DETECTED: Wipe and reload full history
                        log.warn("  ⚠️ Data gap detected for {} (Count: {}). Wiping and reloading 365-day history...", 
                                coinId, count);
                        
                        if (count > 0) {
                            cryptoCandleRepository.deleteByCryptoId(coinId);
                            log.info("  🗑️ Deleted {} incomplete candles for {}", count, coinId);
                        }
                        
                        log.info("  🔄 INITIAL LOAD for {} (using market_chart)", coinId);
                        candles = fetchHistoryFromMarketChart(coinId);
                    } else {
                        // DATA HEALTHY: Fetch only today's update
                        log.info("  ✅ Data healthy for {} (Count: {}). Fetching today's update.", coinId, count);
                        log.info("  🔄 DAILY UPDATE for {} (using ohlc)", coinId);
                        candles = fetchDailyFromOHLC(coinId);
                    }
                    
                    if (!candles.isEmpty()) {
                        cryptoCandleRepository.saveAll(candles);
                        totalCandles += candles.size();
                        processed++;
                        
                        log.info("  ✅ Saved {} candles for {}", candles.size(), coinId);
                    }
                    
                } catch (Exception e) {
                    log.error("  ❌ Failed to fetch candle for {}: {}", coinId, e.getMessage());
                }
            }
            
            // PRUNING: Delete candles older than 365 days
            log.info("🗑️ Pruning old candle data (keeping last 365 days)...");
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(365);
            cryptoCandleRepository.deleteByCandleDateBefore(cutoffDate);
            log.info("✅ Pruning completed (cutoff: {})", cutoffDate);
            
            // Clear cache after all updates
            cryptoCacheService.clearAllCache();
            log.info("✅ Candle update completed: {}/{} coins processed, {} total candles saved", 
                    processed, TRACKED_COINS.size(), totalCandles);
            
        } catch (Exception e) {
            log.error("❌ Candle update failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update candles", e);
        }
    }
    
    /**
     * Full market update - Combined operation
     * Updates both snapshots and candles sequentially
     */
    public void fullMarketUpdate() {
        log.info("🚀 Starting FULL market update...");
        
        updateOnlySnapshots();
        updateOnlyCandles();
        
        log.info("🎉 FULL market update completed successfully!");
    }
}
