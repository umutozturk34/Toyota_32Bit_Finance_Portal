package com.finance.user.client;

import com.finance.user.config.KeycloakAdminProperties;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** Defines the dedicated {@code keycloakWebClient} bean pre-configured with the Keycloak base URL. */
@Configuration
public class KeycloakWebClientConfig {

    @Bean("keycloakWebClient")
    public WebClient keycloakWebClient(WebClient.Builder builder, KeycloakAdminProperties properties) {
        // Bound connect/response time so a hung Keycloak can't pin the Tomcat workers blocking on admin calls.
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.getResponseTimeoutSeconds()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMillis());

        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
