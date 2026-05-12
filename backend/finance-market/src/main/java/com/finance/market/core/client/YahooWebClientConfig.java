package com.finance.market.core.client;

import com.finance.common.config.AppProperties;
import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class YahooWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

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
