package com.finance.market.viop.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Builds the {@link WebClient} used to fetch VİOP (Turkish derivatives) market data.
 */
@Configuration
@EnableConfigurationProperties(ViopProperties.class)
public class ViopWebClientConfig {

    /**
     * Creates the VİOP client configured from {@link ViopProperties}: connect and
     * read/write timeouts derived from the request timeout, transparent gzip handling and
     * redirect following, browser-like headers, and an enlarged in-memory buffer for
     * sizeable JSON responses.
     */
    @Bean
    public WebClient viopWebClient(ViopProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.requestTimeout().toMillis())
                .compress(true)
                .followRedirect(true)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler((int) properties.requestTimeout().toSeconds()))
                        .addHandlerLast(new WriteTimeoutHandler((int) properties.requestTimeout().toSeconds())));

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9")
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE
                        + ", text/javascript, */*; q=0.01")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
    }
}
