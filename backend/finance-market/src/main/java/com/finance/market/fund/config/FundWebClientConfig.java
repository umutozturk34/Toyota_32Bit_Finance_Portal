package com.finance.market.fund.config;
import com.finance.common.config.AppProperties;



import com.finance.shared.filter.RateLimiterFilter;
import com.finance.market.core.filter.TefasSessionFilter;
import com.finance.market.core.filter.TefasSessionManager;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds the {@link WebClient} beans used to talk to TEFAS.
 *
 * <p>Both clients point at the configured TEFAS base URL, raise the in-memory buffer
 * limit so large bulk responses can be decoded, spoof browser headers (TEFAS rejects
 * non-browser callers) and are throttled through the shared {@code tefas} rate limiter.
 * The session-aware variant additionally attaches a {@link TefasSessionFilter} so
 * requests carry a valid TEFAS session cookie.
 */
@Configuration
@RequiredArgsConstructor
public class FundWebClientConfig {

    private static final int BYTES_PER_MB = 1024 * 1024;

    private final AppProperties appProperties;
    private final FundProperties fundProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Stateless TEFAS client (no session cookie) for endpoints that do not require an
     * established TEFAS session, such as initial session bootstrap or public lookups.
     */
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

    /**
     * Session-aware TEFAS client used for the protected data endpoints; a
     * {@link TefasSessionFilter} injects the current TEFAS session cookie supplied by
     * the {@link TefasSessionManager}.
     */
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
