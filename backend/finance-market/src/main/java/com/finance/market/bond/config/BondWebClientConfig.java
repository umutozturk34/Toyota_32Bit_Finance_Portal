package com.finance.market.bond.config;
import com.finance.common.config.AppProperties;


import com.finance.common.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class BondWebClientConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean("bondWebClient")
    public WebClient bondWebClient(WebClient.Builder builder,
                                   @Value("${EVDS_API_KEY:}") String apiKey) {
        AppProperties.BondProvider bond = appProperties.getApi().getBond();
        return builder
                .clone()
                .baseUrl(bond.getBaseUrl())
                .defaultHeader(bond.getApiKeyHeader(), apiKey)
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(appProperties.getHttp().getBondMaxInMemorySizeMb() * 1024 * 1024))
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("bond")))
                .build();
    }
}
