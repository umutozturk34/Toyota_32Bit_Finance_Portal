package com.finance.market.core.client;

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
public class EvdsWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean("evdsWebClient")
    public WebClient evdsWebClient(WebClient.Builder builder,
                                    @Value("${EVDS_API_KEY:}") String apiKey) {
        AppProperties.EvdsProvider evds = appProperties.getApi().getEvds();
        return builder
                .clone()
                .baseUrl(evds.getBaseUrl())
                .defaultHeader(evds.getApiKeyHeader(), apiKey)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(appProperties.getHttp().getEvdsMaxInMemorySizeMb() * 1024 * 1024))
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("evds")))
                .build();
    }
}
