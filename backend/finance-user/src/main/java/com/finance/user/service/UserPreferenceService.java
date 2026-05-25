package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.user.dto.enums.ThemePreference;
import com.finance.user.mapper.UserPreferenceMapper;
import com.finance.user.model.UserPreference;
import com.finance.user.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("tr", "en");

    private final UserPreferenceRepository repository;
    private final UserPreferenceMapper mapper;
    private final KeycloakAdminClient keycloakAdminClient;
    private final UserSecurityProperties securityProperties;

    @Transactional
    public UserPreferenceResponse getOrDefault(String userSub) {
        Optional<UserPreference> existing = repository.findById(userSub);
        if (existing.isPresent()) return mapper.toResponse(existing.get());
        UserPreference seed = UserPreference.defaultsFor(userSub);
        hydrateFromKeycloak(userSub, seed);
        return mapper.toResponse(repository.save(seed));
    }

    private void hydrateFromKeycloak(String userSub, UserPreference target) {
        try {
            keycloakAdminClient.getUserAttribute(userSub, securityProperties.keycloak().themeAttribute())
                    .map(String::toUpperCase)
                    .flatMap(this::parseTheme)
                    .ifPresent(target::setTheme);
            keycloakAdminClient.getUserAttribute(userSub, securityProperties.keycloak().localeAttribute())
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                    .filter(SUPPORTED_LANGUAGES::contains)
                    .ifPresent(target::setLanguage);
        } catch (RuntimeException ex) {
            log.warn("Keycloak attribute hydration failed user={}: {}", userSub, ex.getMessage());
        }
    }

    private Optional<ThemePreference> parseTheme(String raw) {
        try {
            return Optional.of(ThemePreference.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserPreferenceResponse> findPersisted(String userSub) {
        return repository.findById(userSub).map(mapper::toResponse);
    }

    @Transactional
    public UserPreferenceResponse upsert(String userSub, UserPreferenceUpdateRequest request) {
        if (request.language() != null) {
            syncToKeycloak(userSub, securityProperties.keycloak().localeAttribute(), request.language());
        }
        if (request.theme() != null) {
            syncToKeycloak(userSub, securityProperties.keycloak().themeAttribute(), request.theme().name());
        }
        UserPreference entity = repository.findById(userSub)
                .orElseGet(() -> UserPreference.defaultsFor(userSub));
        applyUpdates(entity, request);
        UserPreference saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    private void syncToKeycloak(String userSub, String attribute, String value) {
        try {
            keycloakAdminClient.setUserAttribute(userSub, attribute, value);
        } catch (RuntimeException ex) {
            log.error("Keycloak attribute sync failed user={} attribute={} value={}: {}",
                    userSub, attribute, value, ex.getMessage());
            throw new com.finance.common.exception.BusinessException("error.preferences.syncFailed", attribute);
        }
    }

    private void applyUpdates(UserPreference entity, UserPreferenceUpdateRequest request) {
        if (request.theme() != null) entity.setTheme(request.theme());
        if (request.language() != null) entity.setLanguage(request.language());
        if (request.timezone() != null) entity.setTimezone(request.timezone());
        if (request.defaultChartRange() != null) entity.setDefaultChartRange(request.defaultChartRange());
        if (request.onboardingCompleted() != null) entity.setOnboardingCompleted(request.onboardingCompleted());
    }
}
