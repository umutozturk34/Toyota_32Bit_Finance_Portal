package com.finance.user.client;

import com.finance.user.config.KeycloakAdminProperties;
import com.finance.user.dto.KeycloakUser;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Log4j2
@Component
public class KeycloakAdminClient {

    private final WebClient webClient;
    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenManager tokenManager;
    private final KeycloakAdminExceptionTranslator translator;

    public KeycloakAdminClient(@Qualifier("keycloakWebClient") WebClient webClient,
                                KeycloakAdminProperties properties,
                                KeycloakAdminTokenManager tokenManager,
                                KeycloakAdminExceptionTranslator translator) {
        this.webClient = webClient;
        this.properties = properties;
        this.tokenManager = tokenManager;
        this.translator = translator;
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
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

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
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

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public Map<String, Object> getUser(String userId) {
        return fetchUser(userId);
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public void ensureRealmFlag(String flag, Object value) {
        Map<String, Object> realm = executeWithRetry("fetchRealm", token -> webClient.get()
                .uri("/admin/realms/{realm}", properties.getRealm())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block());
        if (realm == null) {
            throw new com.finance.common.exception.ExternalApiException("KEYCLOAK", "fetchRealm returned null");
        }
        if (value.equals(realm.get(flag))) return;
        Map<String, Object> body = new java.util.HashMap<>(realm);
        body.put(flag, value);
        executeWithRetry("updateRealm", token -> webClient.put()
                .uri("/admin/realms/{realm}", properties.getRealm())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block());
        log.info("Keycloak realm flag synced: {}={}", flag, value);
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public void updateBasics(String userId, String username, String firstName, String lastName) {
        Map<String, Object> body = new java.util.HashMap<>(fetchUser(userId));
        if (username != null) body.put("username", username);
        if (firstName != null) body.put("firstName", firstName);
        if (lastName != null) body.put("lastName", lastName);
        putFullUser("updateBasics", userId, body);
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public void setEnabled(String userId, boolean enabled) {
        Map<String, Object> body = new java.util.HashMap<>(fetchUser(userId));
        body.put("enabled", enabled);
        putFullUser("setEnabled", userId, body);
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public void setEmail(String userId, String newEmail) {
        Map<String, Object> body = new java.util.HashMap<>(fetchUser(userId));
        body.put("email", newEmail);
        body.put("emailVerified", true);
        putFullUser("setEmail", userId, body);
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public String getEmail(String userId) {
        Object email = fetchUser(userId).get("email");
        return email == null ? null : email.toString();
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    @SuppressWarnings("unchecked")
    public java.util.Optional<String> getUserAttribute(String userId, String key) {
        Map<String, Object> user = fetchUser(userId);
        Object attrs = user.get("attributes");
        if (!(attrs instanceof Map<?, ?> map)) return java.util.Optional.empty();
        Object value = map.get(key);
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return java.util.Optional.of(String.valueOf(list.get(0)));
        }
        if (value instanceof String s && !s.isBlank()) return java.util.Optional.of(s);
        return java.util.Optional.empty();
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    @SuppressWarnings("unchecked")
    public void setUserAttribute(String userId, String key, String value) {
        Map<String, Object> body = new java.util.HashMap<>(fetchUser(userId));
        Map<String, List<String>> attributes = body.get("attributes") instanceof Map<?, ?> raw
                ? new java.util.HashMap<>((Map<String, List<String>>) raw)
                : new java.util.HashMap<>();
        attributes.put(key, List.of(value));
        body.put("attributes", attributes);
        putFullUser("setUserAttribute", userId, body);
    }

    private void putFullUser(String operation, String userId, Map<String, Object> body) {
        executeWithRetry(operation, token -> webClient.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUser(String userId) {
        Map<String, Object> user = executeWithRetry("fetchUser", token -> webClient.get()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block());
        if (user == null) {
            throw new com.finance.common.exception.ResourceNotFoundException("Keycloak user not found: " + userId);
        }
        return user;
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listCredentials(String userId) {
        List<Map<String, Object>> creds = executeWithRetry("listCredentials", token -> webClient.get()
                .uri("/admin/realms/{realm}/users/{id}/credentials", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block());
        return creds == null ? List.of() : creds;
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
    public void deleteCredential(String userId, String credentialId) {
        executeWithRetry("deleteCredential", token -> webClient.delete()
                .uri("/admin/realms/{realm}/users/{id}/credentials/{credId}",
                        properties.getRealm(), userId, credentialId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    @CircuitBreaker(name = "keycloak-admin")
    @Retry(name = "keycloak-admin")
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
            return call.apply(tokenManager.getToken());
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Keycloak {} got 401, refreshing token and retrying", operation);
                tokenManager.invalidate();
                return retryOnce(operation, call);
            }
            throw translator.translate(operation, ex);
        } catch (WebClientRequestException ex) {
            throw translator.translateNetwork(operation, ex);
        }
    }

    private <T> T retryOnce(String operation, java.util.function.Function<String, T> call) {
        try {
            return call.apply(tokenManager.getToken());
        } catch (WebClientResponseException ex) {
            throw translator.translate(operation, ex);
        } catch (WebClientRequestException ex) {
            throw translator.translateNetwork(operation, ex);
        }
    }
}
