package com.finance.backend.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TwelveDataClient {
    
    private final WebClient webClient;
    
    @Value("${twelvedata.api-key:demo}")
    private String apiKey;
    
    public TwelveDataClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.twelvedata.com").build();
    }
    
    /**
     * Fetch multiple stock quotes in a single API call
     * Twelve Data allows up to 8 symbols per request in free tier
     */
    public Map<String, StockQuote> fetchMultipleQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Map.of();
        }
        
        String symbolsParam = String.join(",", symbols);
        log.info("Fetching quotes for symbols: {}", symbolsParam);
        
        try {
            Mono<Map> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/quote")
                            .queryParam("symbol", symbolsParam)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(15));
            
            Map<String, Object> result = response.block();
            
            if (result == null) {
                log.warn("Null response from Twelve Data API");
                return Map.of();
            }
            
            // Check for error
            if (result.containsKey("code") && result.containsKey("message")) {
                log.error("Twelve Data API error: {} - {}", result.get("code"), result.get("message"));
                return Map.of();
            }
            
            // Parse response - single symbol returns object, multiple returns map of objects
            java.util.HashMap<String, StockQuote> quotes = new java.util.HashMap<>();
            
            if (symbols.size() == 1) {
                // Single symbol response
                StockQuote quote = parseQuote(result);
                if (quote != null && quote.getSymbol() != null) {
                    quotes.put(quote.getSymbol(), quote);
                }
            } else {
                // Multiple symbols response
                for (String symbol : symbols) {
                    Object symbolData = result.get(symbol);
                    if (symbolData instanceof Map) {
                        StockQuote quote = parseQuote((Map<String, Object>) symbolData);
                        if (quote != null && quote.getSymbol() != null) {
                            quotes.put(quote.getSymbol(), quote);
                        }
                    }
                }
            }
            
            log.info("Successfully fetched {} quotes from Twelve Data", quotes.size());
            return quotes;
            
        } catch (Exception e) {
            log.error("Error fetching from Twelve Data API: {}", e.getMessage());
            return Map.of();
        }
    }
    
    /**
     * Fetch a single stock quote
     */
    public StockQuote fetchQuote(String symbol) {
        Map<String, StockQuote> quotes = fetchMultipleQuotes(List.of(symbol));
        return quotes.get(symbol);
    }
    
    private StockQuote parseQuote(Map<String, Object> data) {
        if (data == null || data.containsKey("code")) {
            return null;
        }
        
        try {
            StockQuote quote = new StockQuote();
            quote.setSymbol((String) data.get("symbol"));
            quote.setName((String) data.get("name"));
            quote.setExchange((String) data.get("exchange"));
            quote.setCurrency((String) data.get("currency"));
            
            // Parse numeric fields safely
            quote.setOpen(parseDouble(data.get("open")));
            quote.setHigh(parseDouble(data.get("high")));
            quote.setLow(parseDouble(data.get("low")));
            quote.setClose(parseDouble(data.get("close")));
            quote.setPreviousClose(parseDouble(data.get("previous_close")));
            quote.setChange(parseDouble(data.get("change")));
            quote.setPercentChange(parseDouble(data.get("percent_change")));
            quote.setVolume(parseLong(data.get("volume")));
            
            return quote;
        } catch (Exception e) {
            log.error("Error parsing quote data: {}", e.getMessage());
            return null;
        }
    }
    
    private Double parseDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    @Data
    public static class StockQuote {
        private String symbol;
        private String name;
        private String exchange;
        private String currency;
        private Double open;
        private Double high;
        private Double low;
        private Double close;
        private Double previousClose;
        private Double change;
        private Double percentChange;
        private Long volume;
    }
}
