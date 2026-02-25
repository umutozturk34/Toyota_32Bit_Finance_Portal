package com.finance.backend.client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
@Component
@Slf4j
public class CoinGeckoClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private static final long MIN_REQUEST_INTERVAL_MS = 3000;
    private volatile long lastRequestTimeMs;
    public CoinGeckoClient(RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.api.coingecko.base-url}") String baseUrl,
                           @Value("${COINGECKO_API_KEY}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }
    public List<CoinGeckoMarketDto> fetchMarkets(String vsCurrency, List<String> coinIds) {
        try {
            String url = baseUrl + "/coins/markets"
                    + "?vs_currency=" + vsCurrency
                    + "&ids=" + String.join(",", coinIds)
                    + "&order=market_cap_desc"
                    + "&sparkline=false";
            String response = exchange(url);
            JsonNode markets = objectMapper.readTree(response);
            List<CoinGeckoMarketDto> result = new ArrayList<>();
            for (JsonNode market : markets) {
                result.add(new CoinGeckoMarketDto(
                        market.get("id").asText(),
                        market.get("symbol").asText().toUpperCase(),
                        market.get("name").asText(),
                        market.get("image").asText(),
                        toDecimal(market.get("current_price")),
                        toDecimal(market.get("price_change_24h")),
                        toDecimal(market.get("price_change_percentage_24h")),
                        market.has("market_cap") ? toDecimal(market.get("market_cap")) : null,
                        market.has("total_volume") ? toDecimal(market.get("total_volume")) : null
                ));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to fetch CoinGecko markets ({}): {}", vsCurrency, e.getMessage());
            throw new ExternalApiException("CoinGecko", "Market fetch failed for " + vsCurrency, e);
        }
    }
    public List<CoinGeckoCandleDto> fetchMarketChartRange(String coinId, int days) {
        try {
            long toTimestamp = Instant.now().getEpochSecond();
            long fromTimestamp = Instant.now().minus(days, ChronoUnit.DAYS).getEpochSecond();
            String url = baseUrl + "/coins/" + coinId + "/market_chart/range"
                    + "?vs_currency=usd"
                    + "&from=" + fromTimestamp
                    + "&to=" + toTimestamp;
            String response = exchange(url);
            JsonNode jsonData = objectMapper.readTree(response);
            JsonNode pricesArray = jsonData.get("prices");
            JsonNode volumesArray = jsonData.get("total_volumes");
            if (pricesArray == null || !pricesArray.isArray()) {
                return Collections.emptyList();
            }
            Map<String, Long> volumeMap = buildVolumeMap(volumesArray);
            List<CoinGeckoCandleDto> candles = new ArrayList<>();
            BigDecimal previousClose = null;
            for (JsonNode pricePoint : pricesArray) {
                long timestamp = pricePoint.get(0).asLong();
                BigDecimal price = pricePoint.get(1).decimalValue();
                LocalDateTime candleDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestamp), ZoneId.of("Europe/Istanbul"))
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
                String dateKey = candleDate.toLocalDate().toString();
                Long volume = volumeMap.get(dateKey);
                candles.add(new CoinGeckoCandleDto(coinId, candleDate, open, high, low, close, volume));
                previousClose = close;
            }
            return candles;
        } catch (Exception e) {
            log.error("Failed to fetch market chart range for {}: {}", coinId, e.getMessage());
            throw new ExternalApiException("CoinGecko", "Chart fetch failed for " + coinId, e);
        }
    }
    public List<CoinGeckoCandleDto> fetchDailyOhlc(String coinId) {
        try {
            String url = baseUrl + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=1";
            String response = exchange(url);
            JsonNode ohlcData = objectMapper.readTree(response);
            if (ohlcData == null || !ohlcData.isArray() || ohlcData.isEmpty()) {
                return Collections.emptyList();
            }
            BigDecimal open = ohlcData.get(0).get(1).decimalValue();
            BigDecimal close = ohlcData.get(ohlcData.size() - 1).get(4).decimalValue();
            BigDecimal high = open;
            BigDecimal low = open;
            for (JsonNode candle : ohlcData) {
                BigDecimal h = candle.get(2).decimalValue();
                BigDecimal l = candle.get(3).decimalValue();
                if (h.compareTo(high) > 0) high = h;
                if (l.compareTo(low) < 0) low = l;
            }
            if (close.compareTo(high) > 0) high = close;
            if (close.compareTo(low) < 0) low = close;
            LocalDateTime candleDate = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
            CoinGeckoCandleDto candle = new CoinGeckoCandleDto(
                    coinId, candleDate, open, high, low, close, null);
            return List.of(candle);
        } catch (Exception e) {
            log.error("Failed to fetch OHLC for {}: {}", coinId, e.getMessage());
            throw new ExternalApiException("CoinGecko", "OHLC fetch failed for " + coinId, e);
        }
    }
    private Map<String, Long> buildVolumeMap(JsonNode volumesArray) {
        Map<String, Long> volumeMap = new HashMap<>();
        if (volumesArray == null || !volumesArray.isArray()) {
            return volumeMap;
        }
        for (JsonNode volumePoint : volumesArray) {
            long timestamp = volumePoint.get(0).asLong();
            long volume = volumePoint.get(1).asLong();
            LocalDateTime volumeDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.DAYS);
            String dateKey = volumeDate.toLocalDate().toString();
            volumeMap.merge(dateKey, volume, (a, b) -> Math.max(a, b));
        }
        return volumeMap;
    }
    private synchronized void throttle() {
        long elapsed = System.currentTimeMillis() - lastRequestTimeMs;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try {
                TimeUnit.MILLISECONDS.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTimeMs = System.currentTimeMillis();
    }
    private String exchange(String url) {
        throttle();
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cg-demo-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }
    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.decimalValue();
    }
}
