package com.finance.user.client;

import com.finance.user.config.KeycloakAdminProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Defines the dedicated {@code keycloakWebClient} bean pre-configured with the Keycloak base URL. */
@Configuration
public class KeycloakWebClientConfig {

    @Bean("keycloakWebClient")
    public WebClient keycloakWebClient(WebClient.Builder builder, KeycloakAdminProperties properties) {
        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
