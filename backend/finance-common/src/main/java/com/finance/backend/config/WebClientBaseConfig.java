package com.finance.backend.config;

import com.finance.backend.filter.RateLimiterFilter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebClientBaseConfig {

    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, appProperties.getHttp().getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(appProperties.getHttp().getReadTimeoutMs()))
                .compress(true);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(userAgentFilter(appProperties.getHttp().getDefaultUserAgent()));
    }

    @Bean
    @Primary
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean("yahooWebClient")
    public WebClient yahooWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .baseUrl(appProperties.getApi().getYahoo().getBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("yahoo")))
                .build();
    }

    private ExchangeFilterFunction userAgentFilter(String defaultUserAgent) {
        return (request, next) -> {
            if (request.headers().getFirst(HttpHeaders.USER_AGENT) == null) {
                return next.exchange(
                        ClientRequest.from(request).header(HttpHeaders.USER_AGENT, defaultUserAgent).build());
            }
            return next.exchange(request);
        };
    }
}
