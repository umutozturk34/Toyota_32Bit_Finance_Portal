package com.finance.backend.client;

import com.finance.backend.dto.CoinGeckoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class CoinGeckoClient {
    
    private final WebClient webClient;
    
    @Value("${coingecko.api-key}")
    private String apiKey;
    
    public CoinGeckoClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    @Cacheable(value = "crypto", key = "'top-coins'")
    public List<CoinGeckoResponse> fetchTopCryptos() {
        log.info("Fetching top cryptocurrencies from CoinGecko");
        
        try {
            Mono<List<CoinGeckoResponse>> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.coingecko.com")
                            .path("/api/v3/coins/markets")
                            .queryParam("vs_currency", "usd")
                            .queryParam("order", "market_cap_desc")
                            .queryParam("per_page", 20)
                            .queryParam("page", 1)
                            .queryParam("x_cg_demo_api_key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToFlux(CoinGeckoResponse.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(10));
            
            List<CoinGeckoResponse> result = response.block();
            log.info("Successfully fetched {} cryptocurrencies", result != null ? result.size() : 0);
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching crypto from CoinGecko: {}", e.getMessage());
            return List.of();
        }
    }
}
