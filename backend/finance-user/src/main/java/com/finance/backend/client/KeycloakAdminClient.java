package com.finance.backend.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.backend.config.KeycloakAdminProperties;
import com.finance.backend.dto.KeycloakUser;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

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
        String token = getAdminToken();
        return webClient.get()
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
                .block();
    }

    public long countUsers(String search) {
        String token = getAdminToken();
        Long total = webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/admin/realms/{realm}/users/count");
                    if (search != null && !search.isBlank()) uriBuilder.queryParam("search", search);
                    return uriBuilder.build(properties.getRealm());
                })
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Long.class)
                .block();
        return total != null ? total : 0L;
    }

    public void setEnabled(String userId, boolean enabled) {
        String token = getAdminToken();
        webClient.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new EnabledUpdate(enabled))
                .retrieve()
                .toBodilessEntity()
                .block();
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
        return webClient.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
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
