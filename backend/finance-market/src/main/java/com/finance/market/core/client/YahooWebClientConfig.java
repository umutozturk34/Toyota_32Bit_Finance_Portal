package com.finance.market.core.client;

import com.finance.common.config.AppProperties;
import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration that builds the {@code WebClient} for the Yahoo Finance
 * chart/quote API (used for commodities and other Yahoo-sourced instruments).
 */
@Configuration
@RequiredArgsConstructor
public class YahooWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Builds the Yahoo-bound WebClient with the configured base URL, a
     * Resilience4j rate-limiter filter, and a 16&nbsp;MB in-memory buffer to
     * accommodate large chart responses.
     *
     * @param builder shared WebClient builder (cloned to avoid mutating the prototype)
     * @return the configured Yahoo WebClient bean
     */
    @Bean("yahooWebClient")
    public WebClient yahooWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .baseUrl(appProperties.getApi().getYahoo().getBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("yahoo")))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
