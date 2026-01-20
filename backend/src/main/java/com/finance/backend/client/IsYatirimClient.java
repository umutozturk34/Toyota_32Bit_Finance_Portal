package com.finance.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.backend.dto.StockPriceDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * İş Yatırım BIST Stock Client
 * 
 * İş Yatırım'ın public JSON servisinden BIST hisse verilerini çeker.
 * 15 dakika gecikmeli ama rate limit yok!
 * 
 * API URL: https://www.isyatirim.com.tr/_layouts/15/Isyatirim.Website/Common/Data.aspx/HisseTekil
 */
@Component
@Slf4j
public class IsYatirimClient {

    private final WebClient webClient;
    private static final String BASE_URL = "https://www.isyatirim.com.tr/_layouts/15/Isyatirim.Website/Common/Data.aspx/HisseTekil";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    
    // BIST 50+ hisseleri - En çok işlem gören hisseler
    private static final List<String> BIST_SYMBOLS = Arrays.asList(
            // BIST 30
            "THYAO", "GARAN", "AKBNK", "YKBNK", "EREGL",
            "ASELS", "KCHOL", "SAHOL", "SISE", "TUPRS",
            "BIMAS", "PGSUS", "TCELL", "TAVHL", "SASA",
            "TOASO", "FROTO", "ARCLK", "KOZAL", "EKGYO",
            // BIST 50 ek hisseler
            "HEKTS", "PETKM", "DOHOL", "VESTL", "ENKAI",
            "MGROS", "OYAKC", "KORDS", "TTKOM", "SOKM",
            "ALARK", "TTRAK", "AEFES", "GESAN", "OTKAR",
            "GUBRF", "ISGYO", "KONTR", "ZOREN", "MPARK",
            // Ek popüler hisseler ve GYO'lar
            "AKSEN", "ULKER", "ISMEN", "VAKBN", "HALKB",
            "KRDMD", "CCOLA", "CIMSA", "ISCTR", "TSKB"
    );
    
    // Hisse isimleri
    private static final java.util.Map<String, String> STOCK_NAMES = new java.util.HashMap<>() {{
        // BIST 30
        put("THYAO", "Türk Hava Yolları");
        put("GARAN", "Garanti Bankası");
        put("AKBNK", "Akbank");
        put("YKBNK", "Yapı Kredi Bankası");
        put("EREGL", "Ereğli Demir Çelik");
        put("ASELS", "Aselsan");
        put("KCHOL", "Koç Holding");
        put("SAHOL", "Sabancı Holding");
        put("SISE", "Şişecam");
        put("TUPRS", "Tüpraş");
        put("BIMAS", "BİM Mağazaları");
        put("PGSUS", "Pegasus");
        put("TCELL", "Turkcell");
        put("TAVHL", "TAV Havalimanları");
        put("SASA", "Sasa Polyester");
        put("TOASO", "Tofaş Oto");
        put("FROTO", "Ford Otosan");
        put("ARCLK", "Arçelik");
        put("KOZAL", "Koza Altın");
        put("EKGYO", "Emlak Konut GYO");
        // BIST 50 ek hisseler
        put("HEKTS", "Hektaş");
        put("PETKM", "Petkim");
        put("DOHOL", "Doğan Holding");
        put("VESTL", "Vestel");
        put("ENKAI", "Enka İnşaat");
        put("MGROS", "Migros");
        put("OYAKC", "Oyak Çimento");
        put("KORDS", "Kordsa");
        put("TTKOM", "Türk Telekom");
        put("SOKM", "Şok Marketler");
        put("ALARK", "Alarko Holding");
        put("TTRAK", "Türk Traktör");
        put("AEFES", "Anadolu Efes");
        put("GESAN", "Giresun Fındık");
        put("OTKAR", "Otokar");
        put("GUBRF", "Gübre Fabrikaları");
        put("ISGYO", "İş GYO");
        put("KONTR", "Kontrolmatik");
        put("ZOREN", "Zorlu Enerji");
        put("MPARK", "MLP Sağlık");
        // Ek popüler hisseler
        put("AKSEN", "Aksa Enerji");
        put("ULKER", "Ülker Bisküvi");
        put("ISMEN", "İş Yatırım Menkul");
        put("VAKBN", "Vakıfbank");
        put("HALKB", "Halkbank");
        put("KRDMD", "Kardemir");
        put("CCOLA", "Coca-Cola İçecek");
        put("CIMSA", "Çimsa");
        put("ISCTR", "İş Bankası (C)");
        put("TSKB", "Türkiye Sınai Kalkınma Bankası");
    }};
    
    public IsYatirimClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
    }
    
    /**
     * Tek bir hisse senedinin güncel verisini çeker
     */
    public StockPriceDto fetchStock(String symbol) {
        try {
            String startDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);
            String endDate = LocalDate.now().format(DATE_FORMAT);
            
            String url = String.format("%s?hisse=%s&startdate=%s&enddate=%s", 
                    BASE_URL, symbol, startDate, endDate);
            
            log.debug("Fetching BIST stock {} from İş Yatırım", symbol);
            
            IsYatirimResponse response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(IsYatirimResponse.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            if (response != null && response.isOk() && response.getValue() != null && !response.getValue().isEmpty()) {
                // En son veriyi al
                IsYatirimStockData data = response.getValue().get(response.getValue().size() - 1);
                return mapToDto(data);
            }
            
        } catch (Exception e) {
            log.error("Error fetching BIST stock {} from İş Yatırım: {}", symbol, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Tüm BIST hisselerini çeker (15 dakikada bir)
     */
    @Cacheable(value = "bist-stocks", key = "'isyatirim'")
    public List<StockPriceDto> fetchAllBistStocks() {
        log.info("Fetching BIST stocks from İş Yatırım");
        List<StockPriceDto> stocks = new ArrayList<>();
        
        for (String symbol : BIST_SYMBOLS) {
            try {
                StockPriceDto stock = fetchStock(symbol);
                if (stock != null) {
                    stocks.add(stock);
                    log.info("Fetched BIST stock: {} at ₺{}", symbol, stock.getPrice());
                }
                
                // Rate limit önlemi - her istek arasında 500ms bekle
                Thread.sleep(500);
                
            } catch (Exception e) {
                log.error("Error processing BIST stock {}: {}", symbol, e.getMessage());
            }
        }
        
        log.info("Successfully fetched {} BIST stocks from İş Yatırım", stocks.size());
        return stocks;
    }
    
    /**
     * İş Yatırım response'unu DTO'ya çevirir
     */
    private StockPriceDto mapToDto(IsYatirimStockData data) {
        // Günlük değişim hesapla (kapanış - önceki gün kapanışı yok, AOF ile karşılaştır)
        BigDecimal price = data.getKapanis();
        BigDecimal aof = data.getAof();
        BigDecimal changeAmount = price.subtract(aof);
        BigDecimal changePercent = BigDecimal.ZERO;
        
        if (aof.compareTo(BigDecimal.ZERO) > 0) {
            changePercent = changeAmount.multiply(BigDecimal.valueOf(100)).divide(aof, 2, java.math.RoundingMode.HALF_UP);
        }
        
        return StockPriceDto.builder()
                .symbol(data.getHisseKodu() + ".IS")
                .name(STOCK_NAMES.getOrDefault(data.getHisseKodu(), data.getHisseKodu()))
                .price(price)
                .changeAmount(changeAmount)
                .changePercent(changePercent)
                .open(data.getAof()) // AOF'u açılış olarak kullan
                .high(data.getMax())
                .low(data.getMin())
                .volume(data.getHacim() != null ? data.getHacim().longValue() : 0L)
                .currency("TRY")
                .exchange("BIST")
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // ============ Response DTO'ları ============
    
    @Data
    public static class IsYatirimResponse {
        private boolean ok;
        private String errorCode;
        private String errorDescription;
        private String transactionId;
        private List<IsYatirimStockData> value;
    }
    
    @Data
    public static class IsYatirimStockData {
        @JsonProperty("HGDG_HS_KODU")
        private String hisseKodu;
        
        @JsonProperty("HGDG_TARIH")
        private String tarih;
        
        @JsonProperty("HGDG_KAPANIS")
        private BigDecimal kapanis;
        
        @JsonProperty("HGDG_AOF")
        private BigDecimal aof; // Ağırlıklı Ortalama Fiyat
        
        @JsonProperty("HGDG_MIN")
        private BigDecimal min;
        
        @JsonProperty("HGDG_MAX")
        private BigDecimal max;
        
        @JsonProperty("HGDG_HACIM")
        private BigDecimal hacim;
        
        @JsonProperty("PD")
        private BigDecimal piyasaDegeri;
        
        @JsonProperty("PD_USD")
        private BigDecimal piyasaDegeriUsd;
        
        @JsonProperty("DOLAR_BAZLI_FIYAT")
        private BigDecimal dolarBazliFiyat;
        
        @JsonProperty("END_DEGER")
        private BigDecimal endeksDeger; // BIST 100 değeri
    }
}
