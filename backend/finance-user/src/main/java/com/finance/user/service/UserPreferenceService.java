package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.UserPreferencesUpdatedEvent;
import com.finance.user.mapper.UserPreferenceMapper;
import com.finance.user.model.UserPreference;
import com.finance.user.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository repository;
    private final UserPreferenceMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Optional<EventPublisherPort> kafkaPort;
    private final KeycloakAdminClient keycloakAdminClient;
    private final UserSecurityProperties securityProperties;

    @Transactional(readOnly = true)
    public UserPreferenceResponse getOrDefault(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(UserPreference.defaultsFor(userSub)));
    }

    @Transactional
    public UserPreferenceResponse upsert(String userSub, UserPreferenceUpdateRequest request) {
        UserPreference entity = repository.findById(userSub)
                .orElseGet(() -> UserPreference.defaultsFor(userSub));
        applyUpdates(entity, request);
        UserPreference saved = repository.save(entity);
        eventPublisher.publishEvent(mapper.toUpdatedEvent(saved));
        if (request.language() != null) {
            syncLocaleToKeycloak(userSub, request.language());
        }
        return mapper.toResponse(saved);
    }

    private void syncLocaleToKeycloak(String userSub, String language) {
        try {
            keycloakAdminClient.setUserAttribute(
                    userSub,
                    securityProperties.keycloak().localeAttribute(),
                    language);
        } catch (RuntimeException ex) {
            log.warn("Failed to sync locale to Keycloak user={} language={}: {}",
                    userSub, language, ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserPreferencesCommitted(UserPreferencesUpdatedEvent event) {
        kafkaPort.ifPresent(port -> port.publish(event));
    }

    private void applyUpdates(UserPreference entity, UserPreferenceUpdateRequest request) {
        if (request.theme() != null) entity.setTheme(request.theme());
        if (request.language() != null) entity.setLanguage(request.language());
        if (request.timezone() != null) entity.setTimezone(request.timezone());
        if (request.defaultChartRange() != null) entity.setDefaultChartRange(request.defaultChartRange());
        if (request.reportFrequency() != null) entity.setReportFrequency(request.reportFrequency());
        if (request.onboardingCompleted() != null) entity.setOnboardingCompleted(request.onboardingCompleted());
    }
}
