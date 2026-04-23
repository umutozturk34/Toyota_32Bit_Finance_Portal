package com.finance.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class ForexWebClientConfig {

    private final AppProperties appProperties;

    @Bean("tcmbWebClient")
    public WebClient tcmbWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .baseUrl(appProperties.getTcmb().getBaseUrl())
                .build();
    }
}
