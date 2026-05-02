package com.finance.backend.client;

import com.finance.backend.client.dto.TokenResponse;
import com.finance.backend.client.dto.TokenSnapshot;
import com.finance.backend.config.KeycloakAdminProperties;
import com.finance.backend.exception.ExternalApiException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Component
public class KeycloakAdminTokenManager {

    private static final String TOKEN_CLIENT_ID = "admin-cli";
    private static final String TOKEN_PATH = "/realms/master/protocol/openid-connect/token";

    private final WebClient webClient;
    private final KeycloakAdminProperties properties;
    private final AtomicReference<TokenSnapshot> snapshot = new AtomicReference<>();

    public KeycloakAdminTokenManager(@Qualifier("keycloakWebClient") WebClient webClient,
                                     KeycloakAdminProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String getToken() {
        TokenSnapshot current = snapshot.get();
        if (current != null && current.isValid(properties.getTokenSafetyMarginSeconds())) {
            return current.token();
        }
        TokenResponse fresh = fetchToken();
        TokenSnapshot updated = new TokenSnapshot(
                fresh.accessToken(),
                Instant.now().plusSeconds(fresh.expiresInSeconds()));
        snapshot.set(updated);
        return updated.token();
    }

    public void invalidate() {
        snapshot.set(null);
    }

    private TokenResponse fetchToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TOKEN_CLIENT_ID);
        form.add("username", properties.getAdminUser());
        form.add("password", properties.getAdminPassword());
        try {
            return webClient.post()
                    .uri(TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Keycloak admin token fetch failed: status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ExternalApiException("KEYCLOAK",
                    "admin token fetch failed with status " + ex.getStatusCode().value());
        } catch (WebClientRequestException ex) {
            throw new ExternalApiException("KEYCLOAK",
                    "admin token endpoint unreachable: " + ex.getMessage());
        }
    }
}
