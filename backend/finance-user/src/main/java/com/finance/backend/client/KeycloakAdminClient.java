package com.finance.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.backend.config.KeycloakAdminProperties;
import com.finance.backend.dto.KeycloakUser;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.exception.ResourceNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Component
public class KeycloakAdminClient {

    private final WebClient webClient;
    private final KeycloakAdminProperties properties;
    private final AtomicReference<TokenSnapshot> tokenSnapshot = new AtomicReference<>();

    public KeycloakAdminClient(WebClient.Builder builder, KeycloakAdminProperties properties) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    public List<KeycloakUser> listUsers(int first, int max, String search) {
        return executeWithRetry("listUsers", token -> webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/admin/realms/{realm}/users")
                            .queryParam("first", first)
                            .queryParam("max", max);
                    if (search != null && !search.isBlank()) uriBuilder.queryParam("search", search);
                    return uriBuilder.build(properties.getRealm());
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(KeycloakUser.class)
                .collectList()
                .block());
    }

    public long countUsers(String search) {
        Long total = executeWithRetry("countUsers", token -> webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/admin/realms/{realm}/users/count");
                    if (search != null && !search.isBlank()) uriBuilder.queryParam("search", search);
                    return uriBuilder.build(properties.getRealm());
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Long.class)
                .block());
        return total != null ? total : 0L;
    }

    public void setEnabled(String userId, boolean enabled) {
        executeWithRetry("setEnabled", token -> webClient.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EnabledUpdate(enabled))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    public void sendActionsEmail(String userId, List<String> actions, String clientId, String redirectUri, long lifespanSeconds) {
        executeWithRetry("sendActionsEmail", token -> webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin/realms/{realm}/users/{id}/execute-actions-email")
                        .queryParam("client_id", clientId)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("lifespan", lifespanSeconds)
                        .build(properties.getRealm(), userId))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(actions)
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    private <T> T executeWithRetry(String operation, java.util.function.Function<String, T> call) {
        try {
            return call.apply(getAdminToken());
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Keycloak {} got 401, refreshing token and retrying", operation);
                tokenSnapshot.set(null);
                return retryOnceOrTranslate(operation, call);
            }
            throw translate(operation, ex);
        } catch (WebClientRequestException ex) {
            throw new ExternalApiException("KEYCLOAK", operation + " network failure: " + ex.getMessage());
        }
    }

    private <T> T retryOnceOrTranslate(String operation, java.util.function.Function<String, T> call) {
        try {
            return call.apply(getAdminToken());
        } catch (WebClientResponseException ex) {
            throw translate(operation, ex);
        } catch (WebClientRequestException ex) {
            throw new ExternalApiException("KEYCLOAK", operation + " network failure: " + ex.getMessage());
        }
    }

    private RuntimeException translate(String operation, WebClientResponseException ex) {
        if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException("Keycloak resource not found for " + operation);
        }
        log.error("Keycloak {} failed: status={} body={}", operation, ex.getStatusCode(), ex.getResponseBodyAsString());
        return new ExternalApiException("KEYCLOAK", operation + " failed with status " + ex.getStatusCode().value());
    }

    private String getAdminToken() {
        TokenSnapshot snapshot = tokenSnapshot.get();
        if (snapshot != null && snapshot.isValid(properties.getTokenSafetyMarginSeconds())) {
            return snapshot.token();
        }
        TokenResponse fresh = fetchAdminToken();
        TokenSnapshot updated = new TokenSnapshot(
                fresh.accessToken(),
                Instant.now().plusSeconds(fresh.expiresInSeconds()));
        tokenSnapshot.set(updated);
        return updated.token();
    }

    private TokenResponse fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", properties.getAdminUser());
        form.add("password", properties.getAdminPassword());
        try {
            return webClient.post()
                    .uri("/realms/master/protocol/openid-connect/token")
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

    private record EnabledUpdate(boolean enabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") int expiresInSeconds,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    private record TokenSnapshot(String token, Instant expiresAt) {
        boolean isValid(long safetyMarginSeconds) {
            return Instant.now().isBefore(expiresAt.minusSeconds(safetyMarginSeconds));
        }
    }
}
