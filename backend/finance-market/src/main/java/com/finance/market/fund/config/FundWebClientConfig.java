package com.finance.market.fund.config;
import com.finance.common.config.AppProperties;

import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.common.filter.RateLimiterFilter;
import com.finance.market.core.filter.TefasSessionFilter;
import com.finance.market.core.filter.TefasSessionManager;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class FundWebClientConfig {

    private static final int BYTES_PER_MB = 1024 * 1024;

    private final AppProperties appProperties;
    private final FundProperties fundProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean("tefasBaseWebClient")
    public WebClient tefasBaseWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .baseUrl(appProperties.getTefasBaseUrl())
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeaders(this::applyBrowserHeaders)
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .build();
    }

    @Bean("tefasWebClient")
    public WebClient tefasWebClient(WebClient.Builder builder,
                                    TefasSessionManager sessionManager) {
        return builder
                .clone()
                .baseUrl(appProperties.getTefasBaseUrl())
                .exchangeStrategies(largeBufferStrategies())
                .defaultHeaders(this::applyBrowserHeaders)
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .filter(new TefasSessionFilter(sessionManager))
                .build();
    }

    private ExchangeStrategies largeBufferStrategies() {
        return ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(
                        fundProperties.getTefasMaxResponseMb() * BYTES_PER_MB))
                .build();
    }

    private void applyBrowserHeaders(org.springframework.http.HttpHeaders headers) {
        headers.set("User-Agent", fundProperties.getTefasUserAgent());
        headers.set("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8");
        headers.set("Sec-Fetch-Site", "same-origin");
        headers.set("Sec-Fetch-Mode", "cors");
        headers.set("Sec-Fetch-Dest", "empty");
        headers.set("Origin", appProperties.getTefasBaseUrl());
        headers.set("Referer", appProperties.getTefasBaseUrl() + "/");
    }
}
