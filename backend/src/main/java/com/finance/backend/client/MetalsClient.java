package com.finance.backend.client;

import com.finance.backend.dto.CoinGeckoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class MetalsClient {
    
    private final WebClient webClient;
    
    @Value("${coingecko.api-key:demo}")
    private String apiKey;
    
    @Value("${coingecko.base-url:https://api.coingecko.com/api/v3}")
    private String baseUrl;
    
    public MetalsClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }
    
    // TTL 14 dakika - Scheduled task 15 dakikada bir çalışıyor
    @Cacheable(value = "metals", key = "'precious'")
    public List<CoinGeckoResponse> fetchPreciousMetals() {
        log.info("Fetching precious metals from CoinGecko");
        
        try {
            // Tokenized gold & silver on CoinGecko
            // pax-gold, tether-gold = Gold (~$2700/oz)
            // kinesis-silver = Silver (~$30/oz) - 3oz unit so ~$93
            String url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=pax-gold,tether-gold,kinesis-silver&order=market_cap_desc&per_page=10&page=1&sparkline=false&x_cg_demo_api_key=" + apiKey;
            
            log.info("Calling CoinGecko metals API URL");
            
            CoinGeckoResponse[] response = webClient.get()
                    .uri(url)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(CoinGeckoResponse[].class)
                    .timeout(Duration.ofSeconds(15))
                    .doOnNext(r -> log.info("Received response with {} items", r != null ? r.length : 0))
                    .doOnError(e -> log.error("WebClient error: {}", e.getMessage()))
                    .block();
            
            if (response != null && response.length > 0) {
                List<CoinGeckoResponse> metals = Arrays.asList(response);
                log.info("Successfully fetched {} precious metals: {}", metals.size(), 
                    metals.stream().map(m -> m.getId() + "=" + m.getCurrentPrice()).toList());
                return metals;
            }
            
            log.warn("Empty or null response from CoinGecko metals API");
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Error fetching precious metals: {} - {}", e.getClass().getName(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}