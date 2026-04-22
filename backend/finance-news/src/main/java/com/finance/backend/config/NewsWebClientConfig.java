package com.finance.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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
