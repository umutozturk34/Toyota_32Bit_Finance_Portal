package com.finance.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class CollectApiClient {
    
    private final WebClient webClient;
    
    @Value("${collectapi.api-key}")
    private String apiKey;
    
    public CollectApiClient(@Value("${collectapi.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
    
    /**
     * BIST Canlı Borsa - TÜM hisseler (779+ hisse, GYO'lar, yatırım ortaklıkları dahil)
     * Endpoint: /economy/liveBorsa
     * Günlük limit: 100 istek - günde 1 kez çağrılacak
     */
    @Cacheable(value = "collectapi", key = "'live-borsa'")
    public List<LiveBorsaItem> fetchLiveBorsa() {
        log.info("Fetching ALL BIST stocks from CollectAPI liveBorsa (779+ stocks)");
        
        try {
            LiveBorsaResponse response = webClient.get()
                    .uri("/economy/liveBorsa")
                    .header("authorization", "apikey " + apiKey)
                    .header("content-type", "application/json")
                    .retrieve()
                    .bodyToMono(LiveBorsaResponse.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            
            if (response != null && response.isSuccess() && response.getResult() != null) {
                log.info("Successfully fetched {} BIST stocks from CollectAPI", response.getResult().size());
                return response.getResult();
            }
            
            log.warn("No BIST stocks received from CollectAPI");
            return List.of();
            
        } catch (Exception e) {
            log.error("Error fetching BIST stocks from CollectAPI: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * BIST 100 Endeksi (XU100)
     * Endpoint: /economy/borsaIstanbul
     */
    @Cacheable(value = "collectapi", key = "'bist-index'")
    public BistIndexItem fetchBistIndex() {
        log.info("Fetching BIST 100 index from CollectAPI");
        
        try {
            BistIndexResponse response = webClient.get()
                    .uri("/economy/borsaIstanbul")
                    .header("authorization", "apikey " + apiKey)
                    .header("content-type", "application/json")
                    .retrieve()
                    .bodyToMono(BistIndexResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (response != null && response.isSuccess() && response.getResult() != null && !response.getResult().isEmpty()) {
                log.info("Successfully fetched BIST 100 index: {}", response.getResult().get(0).getCurrent());
                return response.getResult().get(0);
            }
            
            log.warn("No BIST index received from CollectAPI");
            return null;
            
        } catch (Exception e) {
            log.error("Error fetching BIST index from CollectAPI: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Altın fiyatlarını çeker (Gram, Ons, Çeyrek, Yarım, Tam, Ata, vb.)
     * Endpoint: /economy/goldPrice
     * Günlük limit: 100 istek
     */
    @Cacheable(value = "collectapi", key = "'gold-prices'")
    public List<GoldPriceItem> fetchGoldPrices() {
        log.info("Fetching gold prices from CollectAPI");
        
        try {
            GoldPriceResponse response = webClient.get()
                    .uri("/economy/goldPrice")
                    .header("authorization", "apikey " + apiKey)
                    .header("content-type", "application/json")
                    .retrieve()
                    .bodyToMono(GoldPriceResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (response != null && response.isSuccess() && response.getResult() != null) {
                log.info("Successfully fetched {} gold prices from CollectAPI", response.getResult().size());
                return response.getResult();
            }
            
            log.warn("No gold prices received from CollectAPI");
            return List.of();
            
        } catch (Exception e) {
            log.error("Error fetching gold prices from CollectAPI: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Gümüş fiyatlarını çeker
     * Endpoint: /economy/silverPrice  
     */
    @Cacheable(value = "collectapi", key = "'silver-prices'")
    public List<SilverPriceItem> fetchSilverPrices() {
        log.info("Fetching silver prices from CollectAPI");
        
        try {
            SilverPriceResponse response = webClient.get()
                    .uri("/economy/silverPrice")
                    .header("authorization", "apikey " + apiKey)
                    .header("content-type", "application/json")
                    .retrieve()
                    .bodyToMono(SilverPriceResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (response != null && response.isSuccess() && response.getResult() != null) {
                log.info("Successfully fetched {} silver prices from CollectAPI", response.getResult().size());
                return response.getResult();
            }
            
            log.warn("No silver prices received from CollectAPI");
            return List.of();
            
        } catch (Exception e) {
            log.error("Error fetching silver prices from CollectAPI: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Döviz kurlarını çeker
     * Endpoint: /economy/allCurrency
     */
    @Cacheable(value = "collectapi", key = "'currencies'")
    public List<CurrencyItem> fetchCurrencies() {
        log.info("Fetching currencies from CollectAPI");
        
        try {
            CurrencyResponse response = webClient.get()
                    .uri("/economy/allCurrency")
                    .header("authorization", "apikey " + apiKey)
                    .header("content-type", "application/json")
                    .retrieve()
                    .bodyToMono(CurrencyResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            if (response != null && response.isSuccess() && response.getResult() != null) {
                log.info("Successfully fetched {} currencies from CollectAPI", response.getResult().size());
                return response.getResult();
            }
            
            log.warn("No currencies received from CollectAPI");
            return List.of();
            
        } catch (Exception e) {
            log.error("Error fetching currencies from CollectAPI: {}", e.getMessage());
            return List.of();
        }
    }
    
    // ========== Response DTOs ==========
    
    @Data
    public static class LiveBorsaResponse {
        private boolean success;
        private List<LiveBorsaItem> result;
    }
    
    @Data
    public static class LiveBorsaItem {
        private String name;        // "THYAO", "EKGYO", vb.
        private String pricestr;    // "254,50"
        private Double price;       // 254.50
        private String currency;    // "TRY"
        private Double rate;        // Değişim yüzdesi
        private String hacimlot;    // "16.391.665"
        private String hacimtl;     // "214.562.365"
        private String time;        // "18:10:00"
    }
    
    // BIST Index Response
    @Data
    public static class BistIndexResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private boolean success;
        private List<BistIndexItem> result;
    }
    
    @Data
    public static class BistIndexItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private Double current;       // Güncel değer
        private String currentstr;
        private Double changerate;    // Değişim yüzdesi
        private String changeratestr;
        private Double min;           // Gün içi en düşük
        private String minstr;
        private Double max;           // Gün içi en yüksek
        private String maxstr;
        private Double opening;       // Açılış
        private String openingstr;
        private Double closing;       // Kapanış
        private String closingstr;
        private String time;          // Saat
        private String date;          // Tarih
        private String datetime;
    }
    
    @Data
    public static class GoldPriceResponse {
        private boolean success;
        private List<GoldPriceItem> result;
    }
    
    @Data
    public static class GoldPriceItem {
        private String name;        // "Gram Altın", "Çeyrek Altın", vb.
        private String buying;      // Alış fiyatı
        private String selling;     // Satış fiyatı
        @JsonProperty("datetime")
        private String dateTime;
        private String rate;        // Değişim yüzdesi
    }
    
    @Data
    public static class SilverPriceResponse {
        private boolean success;
        private List<SilverPriceItem> result;
    }
    
    @Data
    public static class SilverPriceItem {
        private String name;        // "Gram Gümüş", "Ons Gümüş"
        private String buying;
        private String selling;
        @JsonProperty("datetime")
        private String dateTime;
        private String rate;
    }
    
    @Data
    public static class CurrencyResponse {
        private boolean success;
        private List<CurrencyItem> result;
    }
    
    @Data
    public static class CurrencyItem {
        private String code;        // "USD", "EUR"
        private String name;        // "Amerikan Doları"
        private String buying;
        private String selling;
        @JsonProperty("datetime")
        private String dateTime;
        private String rate;
    }
}
