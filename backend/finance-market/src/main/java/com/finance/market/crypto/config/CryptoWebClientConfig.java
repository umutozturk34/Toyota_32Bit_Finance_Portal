package com.finance.market.crypto.config;
import com.finance.common.config.AppProperties;


import com.finance.shared.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration that builds the crypto data-source {@code WebClient}s:
 * CoinGecko (live market data) and Binance (candle/OHLC history). Each client is
 * pinned to its provider base URL and guarded by its own Resilience4j
 * rate-limiter filter.
 */
@Configuration
@RequiredArgsConstructor
public class CryptoWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    /**
     * Builds the CoinGecko-bound WebClient with the configured base URL, the
     * API-key header injected from {@code COINGECKO_API_KEY}, and a CoinGecko
     * rate-limiter filter.
     *
     * @param builder shared WebClient builder (cloned to avoid mutating the prototype)
     * @param apiKey  the CoinGecko API key resolved from the environment ({@code COINGECKO_API_KEY})
     * @return the configured CoinGecko WebClient bean
     */
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

    /**
     * Builds the Binance-bound WebClient with the configured base URL and a
     * Binance rate-limiter filter, used to source candle/OHLC history.
     *
     * @param builder shared WebClient builder (cloned to avoid mutating the prototype)
     * @return the configured Binance WebClient bean
     */
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
