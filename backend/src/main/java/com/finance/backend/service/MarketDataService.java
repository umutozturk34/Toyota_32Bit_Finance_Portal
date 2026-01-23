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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    
    private static final List<String> TRACKED_COINS = List.of(
        "bitcoin", "ethereum", "tether", "binancecoin", "solana", "ripple",
        "usd-coin", "cardano", "avalanche-2", "dogecoin", "tron", "polkadot"
    );
    
    public void updateOnlySnapshots() {
        try {
            log.info("📊 Starting snapshot update for {} coins...", TRACKED_COINS.size());
            
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
            
            String urlTry = COINGECKO_BASE_URL + "/coins/markets" +
                    "?vs_currency=try" +
                    "&ids=" + String.join(",", TRACKED_COINS) +
                    "&order=market_cap_desc" +
                    "&sparkline=false";
            
            ResponseEntity<String> responseTry = restTemplate.exchange(urlTry, HttpMethod.GET, entity, String.class);
            JsonNode marketsTry = objectMapper.readTree(responseTry.getBody());
            
            List<Crypto> cryptos = new ArrayList<>();
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
                        .marketCap(market.has("market_cap") ? BigDecimal.valueOf(market.get("market_cap").asDouble()) : null)
                        .totalVolume(market.has("total_volume") ? BigDecimal.valueOf(market.get("total_volume").asDouble()) : null)
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

    private void saveOrUpdateCandle(CryptoCandle fetchedCandle) {
        LocalDateTime normalizedDate = fetchedCandle.getCandleDate().truncatedTo(ChronoUnit.DAYS);
        
        Optional<CryptoCandle> existingCandleOpt = cryptoCandleRepository.findByCryptoIdAndCandleDate(
                fetchedCandle.getCryptoId(), 
                normalizedDate
        );

        if (existingCandleOpt.isPresent()) {
            CryptoCandle existing = existingCandleOpt.get();
            existing.setHigh(fetchedCandle.getHigh());
            existing.setLow(fetchedCandle.getLow());
            existing.setClose(fetchedCandle.getClose());
            existing.setOpen(fetchedCandle.getOpen());
            
            cryptoCandleRepository.save(existing);
        } else {
            fetchedCandle.setCandleDate(normalizedDate);
            cryptoCandleRepository.save(fetchedCandle);
        }
    }

    private List<CryptoCandle> fetchHistoryFromMarketChart(String coinId) throws Exception {
        log.info("  📊 Fetching 365-day history from market_chart/range for {}", coinId);
        
        long toTimestamp = Instant.now().getEpochSecond();
        long fromTimestamp = Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS).getEpochSecond();

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
        
        List<CryptoCandle> candles = new ArrayList<>();
        
        if (pricesArray == null || !pricesArray.isArray()) return candles;

        BigDecimal previousClose = null;
        
        for (JsonNode pricePoint : pricesArray) {
            long timestamp = pricePoint.get(0).asLong();
            BigDecimal price = BigDecimal.valueOf(pricePoint.get(1).asDouble());
            
            LocalDateTime candleDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                                                    .truncatedTo(ChronoUnit.DAYS);
            
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
                    .open(open).high(high).low(low).close(close)
                    .build();
            
            candles.add(candle);
            previousClose = close;
        }
        return candles;
    }
    
    private List<CryptoCandle> fetchDailyFromOHLC(String coinId) throws Exception {
        log.info("  📈 Fetching today's OHLC for {}", coinId);
        
        String url = COINGECKO_BASE_URL + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=1";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cg-demo-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode ohlcData = objectMapper.readTree(response.getBody());
        
        List<CryptoCandle> candles = new ArrayList<>();
        if (ohlcData == null || !ohlcData.isArray() || ohlcData.size() == 0) return candles;
        
        JsonNode latestCandle = ohlcData.get(ohlcData.size() - 1);
        
        CryptoCandle candle = CryptoCandle.builder()
                .cryptoId(coinId)
                .candleDate(LocalDateTime.now())
                .open(BigDecimal.valueOf(latestCandle.get(1).asDouble()))
                .high(BigDecimal.valueOf(latestCandle.get(2).asDouble()))
                .low(BigDecimal.valueOf(latestCandle.get(3).asDouble()))
                .close(BigDecimal.valueOf(latestCandle.get(4).asDouble()))
                .build();
        
        candles.add(candle);
        return candles;
    }
    
    public void updateOnlyCandles() {
        try {
            log.info("📈 Starting HYBRID candle update with SELF-HEALING for {} coins...", TRACKED_COINS.size());
            
            int processed = 0;
            
            for (String coinId : TRACKED_COINS) {
                try {
                    if (processed > 0) Thread.sleep(3000);
                    
                    long count = cryptoCandleRepository.countByCryptoId(coinId);
                    List<CryptoCandle> candles;
                    
                    if (count < 350) {
                        log.warn("  ⚠️ Gap detected for {}. Reloading full history...", coinId);
                        cryptoCandleRepository.deleteByCryptoId(coinId);
                        
                        candles = fetchHistoryFromMarketChart(coinId);
                        

                        if (!candles.isEmpty()) {
                            cryptoCandleRepository.saveAll(candles);
                        }
                    } else {
                        log.info("  ✅ Data healthy for {}. Fetching today's update.", coinId);
                        candles = fetchDailyFromOHLC(coinId);
                        
                        for (CryptoCandle candle : candles) {
                            saveOrUpdateCandle(candle);
                        }
                    }
                    
                    if (!candles.isEmpty()) processed++;
                    
                } catch (Exception e) {
                    log.error("  ❌ Failed to fetch candle for {}: {}", coinId, e.getMessage());
                }
            }
            
            log.info("🗑️ Pruning old candle data...");
            cryptoCandleRepository.deleteByCandleDateBefore(LocalDateTime.now().minusDays(365));
            
            cryptoCacheService.clearAllCache();
            log.info("✅ Candle update completed successfully.");
            
        } catch (Exception e) {
            log.error("❌ Candle update failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update candles", e);
        }
    }
    
    public void fullMarketUpdate() {
        log.info("🚀 Starting FULL market update...");
        updateOnlySnapshots();
        updateOnlyCandles();
        log.info("🎉 FULL market update completed!");
    }
}