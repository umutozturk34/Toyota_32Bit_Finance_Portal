package com.finance.backend.client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.dto.external.CoinGeckoCandleDto;
import com.finance.backend.dto.external.CoinGeckoMarketDto;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.CryptoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
@Component
@Slf4j
public class CoinGeckoClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CryptoMapper cryptoMapper;
    private final String baseUrl;
    private final String apiKey;
    private static final long MIN_REQUEST_INTERVAL_MS = 3000;
    private volatile long lastRequestTimeMs;
    public CoinGeckoClient(RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           CryptoMapper cryptoMapper,
                           @Value("${app.api.coingecko.base-url}") String baseUrl,
                           @Value("${COINGECKO_API_KEY}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.cryptoMapper = cryptoMapper;
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
            return cryptoMapper.toMarketDtos(markets);
        } catch (ExternalApiException e) {
            throw e;
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
            return cryptoMapper.toCandleDtosFromRange(jsonData, coinId);
        } catch (ExternalApiException e) {
            throw e;
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
            return cryptoMapper.toCandleDtoFromOhlc(ohlcData, coinId);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch OHLC for {}: {}", coinId, e.getMessage());
            throw new ExternalApiException("CoinGecko", "OHLC fetch failed for " + coinId, e);
        }
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
}
