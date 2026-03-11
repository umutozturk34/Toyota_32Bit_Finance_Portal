package com.finance.backend.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finance.backend.client.RateLimiterFilter;
import com.finance.backend.client.TefasSessionFilter;
import com.finance.backend.client.TefasSessionManager;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
@Configuration
@RequiredArgsConstructor
public class AppConfig {
    private final AppProperties appProperties;
    private final RateLimiterRegistry rateLimiterRegistry;

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    @Scope("prototype")
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    private HttpClient baseHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, appProperties.getHttp().getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(appProperties.getHttp().getReadTimeoutMs()))
                .compress(true);
    }

    private ExchangeFilterFunction userAgentFilter() {
        String defaultUserAgent = appProperties.getHttp().getDefaultUserAgent();
        return (request, next) -> {
            if (request.headers().getFirst(HttpHeaders.USER_AGENT) == null) {
                return next.exchange(
                        org.springframework.web.reactive.function.client.ClientRequest.from(request)
                                .header(HttpHeaders.USER_AGENT, defaultUserAgent)
                                .build());
            }
            return next.exchange(request);
        };
    }

    @Bean
    @Primary
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .filter(userAgentFilter())
                .build();
    }
    @Bean("yahooWebClient")
    public WebClient yahooWebClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .baseUrl(appProperties.getApi().getYahoo().getBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("yahoo")))
                .filter(userAgentFilter())
                .build();
    }
    @Bean("tefasBaseWebClient")
    public WebClient tefasBaseWebClient(WebClient.Builder builder) {
        return builder 
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .baseUrl(appProperties.getTefasBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .filter(userAgentFilter())
                .build();
    }
    @Bean("tefasWebClient")
    public WebClient tefasWebClient(WebClient.Builder builder,
                                    TefasSessionManager sessionManager) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .baseUrl(appProperties.getTefasBaseUrl())
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("tefas")))
                .filter(new TefasSessionFilter(sessionManager))
                .filter(userAgentFilter())
                .build();
    }
    @Bean("tcmbWebClient")
    public WebClient tcmbWebClient(WebClient.Builder builder) {
        return builder
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .baseUrl(appProperties.getTcmb().getBaseUrl())
                .filter(userAgentFilter())
                .build();
    }
    @Bean("coinGeckoWebClient")
    public WebClient coinGeckoWebClient(WebClient.Builder builder,
                                        @Value("${COINGECKO_API_KEY:}") String apiKey) {
        AppProperties.CoinGeckoProvider cg = appProperties.getApi().getCoingecko();
        return builder
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .baseUrl(cg.getBaseUrl())
                .defaultHeader(cg.getApiKeyHeader(), apiKey)
                .filter(new RateLimiterFilter(rateLimiterRegistry.rateLimiter("coingecko")))
                .filter(userAgentFilter())
                .build();
    }
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        AppProperties.Async async = appProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
