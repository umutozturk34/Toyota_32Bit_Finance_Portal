package com.finance.notification.core.dispatch;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Component
@Primary
public class KeycloakUserEmailLookup implements UserEmailLookup {

    private static final String TOKEN_PATH = "/realms/master/protocol/openid-connect/token";
    private static final String TOKEN_CLIENT_ID = "admin-cli";

    private final WebClient webClient;
    private final String realm;
    private final String adminUser;
    private final String adminPassword;
    private final Cache<String, KeycloakUserProfile> profileCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();
    private final AtomicReference<TokenSnapshot> tokenSnapshot = new AtomicReference<>();

    public KeycloakUserEmailLookup(@Value("${keycloak.base-url}") String baseUrl,
                                   @Value("${keycloak.realm}") String realm,
                                   @Value("${keycloak.admin-user}") String adminUser,
                                   @Value("${keycloak.admin-password}") String adminPassword) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @Override
    public Optional<String> findEmail(String userSub) {
        return findProfile(userSub).map(KeycloakUserProfile::email).filter(s -> !s.isBlank());
    }

    public Optional<KeycloakUserProfile> findProfile(String userSub) {
        KeycloakUserProfile cached = profileCache.getIfPresent(userSub);
        if (cached != null) return Optional.of(cached);
        try {
            String token = ensureToken();
            Map<String, Object> user = webClient.get()
                    .uri("/admin/realms/{realm}/users/{id}", realm, userSub)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (user == null) return Optional.empty();
            KeycloakUserProfile profile = new KeycloakUserProfile(
                    userSub,
                    asString(user.get("username")),
                    asString(user.get("email")),
                    asString(user.get("firstName")),
                    asString(user.get("lastName")));
            profileCache.put(userSub, profile);
            return Optional.of(profile);
        } catch (Exception e) {
            log.warn("Profile lookup failed user={}: {}", userSub, e.getMessage());
            return Optional.empty();
        }
    }

    public java.util.List<KeycloakUserProfile> search(String query, int max) {
        if (query == null || query.isBlank()) return java.util.List.of();
        try {
            String token = ensureToken();
            java.util.List<Map<String, Object>> users = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users")
                            .queryParam("search", query)
                            .queryParam("max", Math.max(1, Math.min(max, 200)))
                            .build(realm))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(java.util.List.class)
                    .block();
            if (users == null) return java.util.List.of();
            java.util.List<KeycloakUserProfile> result = new java.util.ArrayList<>(users.size());
            for (Map<String, Object> user : users) {
                String sub = asString(user.get("id"));
                if (sub == null) continue;
                KeycloakUserProfile profile = new KeycloakUserProfile(
                        sub,
                        asString(user.get("username")),
                        asString(user.get("email")),
                        asString(user.get("firstName")),
                        asString(user.get("lastName")));
                profileCache.put(sub, profile);
                result.add(profile);
            }
            return result;
        } catch (Exception e) {
            log.warn("Keycloak search failed query={}: {}", query, e.getMessage());
            return java.util.List.of();
        }
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private String ensureToken() {
        TokenSnapshot current = tokenSnapshot.get();
        if (current != null && current.isValid()) return current.token();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", TOKEN_CLIENT_ID);
        form.add("username", adminUser);
        form.add("password", adminPassword);

        Map<String, Object> response = webClient.post()
                .uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IllegalStateException("Keycloak token response missing access_token");
        }
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 60L;
        TokenSnapshot fresh = new TokenSnapshot(token, Instant.now().plusSeconds(expiresIn - 30));
        tokenSnapshot.set(fresh);
        return token;
    }

    private record TokenSnapshot(String token, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
