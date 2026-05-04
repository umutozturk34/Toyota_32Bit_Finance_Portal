package com.finance.user.client;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.user.client.dto.EnabledUpdate;
import com.finance.user.config.KeycloakAdminProperties;
import com.finance.user.dto.KeycloakUser;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public void setUserAttribute(String userId, String key, String value) {
        Map<String, List<String>> existing = fetchAttributes(userId);
        Map<String, List<String>> merged = Stream.concat(
                existing.entrySet().stream(),
                Stream.of(Map.entry(key, List.of(value)))
        ).collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (oldValue, newValue) -> newValue));
        executeWithRetry("setUserAttribute", token -> webClient.put()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("attributes", merged))
                .retrieve()
                .toBodilessEntity()
                .block());
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> fetchAttributes(String userId) {
        Map<String, Object> user = executeWithRetry("fetchUserForAttributes", token -> webClient.get()
                .uri("/admin/realms/{realm}/users/{id}", properties.getRealm(), userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block());
        Object attributes = user == null ? null : user.get("attributes");
        if (attributes instanceof Map<?, ?> raw) {
            return raw.entrySet().stream()
                    .filter(e -> e.getValue() instanceof List<?>)
                    .collect(Collectors.toUnmodifiableMap(
                            e -> e.getKey().toString(),
                            e -> ((List<?>) e.getValue()).stream().map(Object::toString).toList()));
        }
        return Map.of();
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
