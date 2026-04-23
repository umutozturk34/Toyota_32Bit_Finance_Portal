package com.finance.backend.config;

import com.finance.backend.filter.RateLimiterFilter;
import com.finance.backend.filter.TefasSessionFilter;
import com.finance.backend.filter.TefasSessionManager;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class FundWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean("tefasBaseWebClient")
    public WebClient tefasBaseWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .baseUrl(appProperties.getTefasBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .build();
    }

    @Bean("tefasWebClient")
    public WebClient tefasWebClient(WebClient.Builder builder,
                                    TefasSessionManager sessionManager) {
        return builder
                .clone()
                .baseUrl(appProperties.getTefasBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .filter(new TefasSessionFilter(sessionManager))
                .build();
    }
}
