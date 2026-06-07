package com.finance.market.core.client;

import com.finance.common.config.AppProperties;
import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration that builds the {@code WebClient} for the CBRT EVDS data
 * provider (used for bond, FX and macro series).
 */
@Configuration
@RequiredArgsConstructor
public class EvdsWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Builds the EVDS-bound WebClient with the configured base URL, the API-key
     * header injected from {@code EVDS_API_KEY}, an enlarged in-memory buffer for
     * large series responses, and a Resilience4j rate-limiter filter.
     *
     * @param builder shared WebClient builder (cloned to avoid mutating the prototype)
     * @param apiKey  the EVDS API key resolved from the environment ({@code EVDS_API_KEY})
     * @return the configured EVDS WebClient bean
     */
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
