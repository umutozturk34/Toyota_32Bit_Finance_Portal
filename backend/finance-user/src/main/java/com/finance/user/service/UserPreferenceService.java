package com.finance.user.service;

import com.finance.common.event.UserRegisteredEvent;
import com.finance.shared.event.EventPublisherPort;
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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user preferences and keeps theme/locale in sync with Keycloak. First read lazily seeds a
 * default row (hydrating theme/locale from Keycloak attributes) and publishes a {@code UserRegisteredEvent},
 * effectively treating the initial preference fetch as user onboarding. Updates are partial and push
 * theme/language back to Keycloak, failing the update if that sync fails.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("tr", "en");
    // Every account should carry the product's baseline realm role. Keycloak self-registration only grants the
    // default-roles composite, so we attach this ourselves on first touch.
    private static final String BASELINE_ROLE = "USER";

    // Subjects already reconciled this boot, so the baseline-role check costs one Keycloak round-trip per user per
    // application lifetime instead of one per preferences read.
    private final Set<String> baselineRoleEnsured = ConcurrentHashMap.newKeySet();

    private final UserPreferenceRepository repository;
    private final UserPreferenceMapper mapper;
    private final KeycloakAdminClient keycloakAdminClient;
    private final UserSecurityProperties securityProperties;
    private final EventPublisherPort eventPublisher;

    /**
     * Returns the user's preferences, lazily creating the default row on first access (seeded from
     * Keycloak attributes) and publishing a {@code UserRegisteredEvent} for downstream onboarding.
     */
    @Transactional
    public UserPreferenceResponse getOrDefault(String userSub) {
        Optional<UserPreference> existing = repository.findById(userSub);
        if (existing.isPresent()) {
            ensureBaselineRole(userSub);
            return mapper.toResponse(existing.get());
        }
        UserPreference seed = UserPreference.defaultsFor(userSub);
        hydrateFromKeycloak(userSub, seed);
        UserPreference saved = repository.save(seed);
        ensureBaselineRole(userSub);
        eventPublisher.publish(new UserRegisteredEvent(
                UUID.randomUUID().toString(), userSub, OffsetDateTime.now()));
        return mapper.toResponse(saved);
    }

    /**
     * Best-effort attachment of the baseline {@code USER} realm role, attempted at most once per subject per boot.
     * Keycloak self-registration leaves users with no product role, so without this they show up role-less and any
     * future {@code hasRole('USER')} gate would lock them out. A transient failure is rolled back so the next visit
     * retries, and never propagates — onboarding must not hinge on the role write.
     */
    private void ensureBaselineRole(String userSub) {
        if (!baselineRoleEnsured.add(userSub)) return;
        try {
            keycloakAdminClient.ensureRealmRole(userSub, BASELINE_ROLE);
        } catch (RuntimeException ex) {
            baselineRoleEnsured.remove(userSub);
            log.warn("Baseline {} role grant failed user={}: {}", BASELINE_ROLE, userSub, ex.getMessage());
        }
    }

    /** Best-effort override of the seed's theme/language from Keycloak attributes; failures leave defaults intact. */
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

    /**
     * Applies a partial preference update (null fields untouched). Language and theme are synced to
     * Keycloak first; a sync failure aborts the whole update so persisted state never diverges.
     */
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

    /** Writes a single attribute to Keycloak, translating any failure into a business error to abort the update. */
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
