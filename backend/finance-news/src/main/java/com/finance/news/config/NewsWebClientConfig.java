package com.finance.news.config;
import com.finance.common.config.AppProperties;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Defines a dedicated {@code newsWebClient} whose in-memory buffer is enlarged to hold full RSS payloads. */
@Configuration
@RequiredArgsConstructor
public class NewsWebClientConfig {

    private final AppProperties appProperties;

    @Bean("newsWebClient")
    public WebClient newsWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(appProperties.getHttp().getNewsMaxInMemorySizeMb() * 1024 * 1024))
                .build();
    }
}
