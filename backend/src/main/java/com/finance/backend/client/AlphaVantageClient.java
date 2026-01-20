package com.finance.backend.client;

import com.finance.backend.dto.AlphaVantageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class AlphaVantageClient {
    
    private final WebClient webClient;
    
    @Value("${alphavantage.api-key}")
    private String apiKey;
    
    public AlphaVantageClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    // Cache disabled for stock fetches to avoid caching rate limit errors
    public AlphaVantageResponse fetchStockQuote(String symbol) {
        log.info("Fetching stock quote for: {}", symbol);
        
        try {
            Mono<AlphaVantageResponse> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("www.alphavantage.co")
                            .path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(AlphaVantageResponse.class)
                    .timeout(Duration.ofSeconds(10));
            
            AlphaVantageResponse result = response.block();
            
            // Check for API rate limit or errors
            if (result != null && result.getInformation() != null) {
                log.warn("AlphaVantage API limit: {}", result.getInformation());
                return null;
            }
            if (result != null && result.getNote() != null) {
                log.warn("AlphaVantage API note: {}", result.getNote());
            }
            
            if (result != null && result.getGlobalQuote() != null) {
                log.info("Successfully fetched quote for {}", symbol);
            } else {
                log.warn("No quote data returned for {}", symbol);
            }
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching stock from Alpha Vantage: {}", e.getMessage());
            return null;
        }
    }
}
