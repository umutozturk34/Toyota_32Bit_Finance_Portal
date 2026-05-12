package com.finance.market.crypto.config;
import com.finance.common.config.AppProperties;


import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class CryptoWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean("coinGeckoWebClient")
    public WebClient coinGeckoWebClient(WebClient.Builder builder,
                                        @Value("${COINGECKO_API_KEY:}") String apiKey) {
        AppProperties.CoinGeckoProvider cg = appProperties.getApi().getCoingecko();
        return builder
                .clone()
                .baseUrl(cg.getBaseUrl())
                .defaultHeader(cg.getApiKeyHeader(), apiKey)
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("coingecko")))
                .build();
    }

    @Bean("binanceWebClient")
    public WebClient binanceWebClient(WebClient.Builder builder) {
        AppProperties.Provider binance = appProperties.getApi().getBinance();
        return builder
                .clone()
                .baseUrl(binance.getBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("binance")))
                .build();
    }
}
