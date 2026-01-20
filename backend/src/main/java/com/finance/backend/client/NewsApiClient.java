package com.finance.backend.client;

import com.finance.backend.dto.NewsApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class NewsApiClient {
    
    private final WebClient webClient;
    
    @Value("${newsapi.api-key:demo}")
    private String apiKey;
    
    @Value("${newsapi.base-url:https://newsapi.org/v2}")
    private String baseUrl;
    
    public NewsApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    
    @Cacheable(value = "news", key = "'business-mixed'")
    public NewsApiResponse fetchBusinessNews() {
        log.info("Fetching business news from NewsAPI.org");
        
        try {
            // Fetch Turkey-specific market news
            Mono<NewsApiResponse> turkeyResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("newsapi.org")
                            .path("/v2/everything")
                            .queryParam("q", "\"Borsa Istanbul\" OR \"BIST 100\" OR \"Turkey stock market\"")
                            .queryParam("language", "en")
                            .queryParam("sortBy", "publishedAt")
                            .queryParam("apiKey", apiKey)
                            .queryParam("pageSize", 5)
                            .build())
                    .retrieve()
                    .bodyToMono(NewsApiResponse.class)
                    .timeout(Duration.ofSeconds(10));
            
            NewsApiResponse turkeyResult = turkeyResponse.block();
            
            // Fetch Crypto news
            Mono<NewsApiResponse> cryptoResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("newsapi.org")
                            .path("/v2/everything")
                            .queryParam("q", "bitcoin OR ethereum OR cryptocurrency")
                            .queryParam("language", "en")
                            .queryParam("sortBy", "publishedAt")
                            .queryParam("apiKey", apiKey)
                            .queryParam("pageSize", 5)
                            .build())
                    .retrieve()
                    .bodyToMono(NewsApiResponse.class)
                    .timeout(Duration.ofSeconds(10));
            
            NewsApiResponse cryptoResult = cryptoResponse.block();
            
            // Fetch US business headlines
            Mono<NewsApiResponse> usResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("newsapi.org")
                            .path("/v2/top-headlines")
                            .queryParam("country", "us")
                            .queryParam("category", "business")
                            .queryParam("apiKey", apiKey)
                            .queryParam("pageSize", 10)
                            .build())
                    .retrieve()
                    .bodyToMono(NewsApiResponse.class)
                    .timeout(Duration.ofSeconds(10));
            
            NewsApiResponse usResult = usResponse.block();
            
            // Combine results (Turkey + Crypto + US)
            NewsApiResponse combinedResult = new NewsApiResponse();
            if (turkeyResult != null && turkeyResult.getArticles() != null) {
                combinedResult.setArticles(new java.util.ArrayList<>(turkeyResult.getArticles()));
                combinedResult.setTotalResults(turkeyResult.getTotalResults());
            } else {
                combinedResult.setArticles(new java.util.ArrayList<>());
                combinedResult.setTotalResults(0);
            }
            
            // Add Crypto news if available
            if (cryptoResult != null && cryptoResult.getArticles() != null) {
                combinedResult.getArticles().addAll(cryptoResult.getArticles());
                combinedResult.setTotalResults(combinedResult.getTotalResults() + cryptoResult.getTotalResults());
            }
            
            // Add US news if available
            if (usResult != null && usResult.getArticles() != null) {
                combinedResult.getArticles().addAll(usResult.getArticles());
                combinedResult.setTotalResults(combinedResult.getTotalResults() + usResult.getTotalResults());
            }
            
            log.info("Successfully fetched {} Turkey articles, {} Crypto articles and {} US articles", 
                    turkeyResult != null && turkeyResult.getArticles() != null ? turkeyResult.getArticles().size() : 0,
                    cryptoResult != null && cryptoResult.getArticles() != null ? cryptoResult.getArticles().size() : 0,
                    usResult != null && usResult.getArticles() != null ? usResult.getArticles().size() : 0);
            return combinedResult;
            
        } catch (Exception e) {
            log.error("Error fetching news from NewsAPI: {}", e.getMessage());
            return createEmptyResponse();
        }
    }
    
    private NewsApiResponse createEmptyResponse() {
        NewsApiResponse response = new NewsApiResponse();
        response.setStatus("error");
        response.setTotalResults(0);
        response.setArticles(java.util.Collections.emptyList());
        return response;
    }
}
