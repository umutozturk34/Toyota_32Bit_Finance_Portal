package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.common.event.UserPreferenceEventPort;
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
    private final Optional<UserPreferenceEventPort> kafkaPort;
    private final Optional<KeycloakAdminClient> keycloakClient;

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
        return mapper.toResponse(saved);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onUserPreferencesCommitted(UserPreferencesUpdatedEvent event) {
        kafkaPort.ifPresent(port -> port.publishUserPreferencesUpdated(event));
        pushThemeToKeycloak(event);
    }

    private void pushThemeToKeycloak(UserPreferencesUpdatedEvent event) {
        keycloakClient.ifPresent(client -> {
            try {
                client.setUserAttribute(event.userSub(), "themePreference", event.theme());
            } catch (RuntimeException ex) {
                log.warn("Failed to push themePreference to Keycloak user={}: {}",
                        event.userSub(), ex.getMessage());
            }
        });
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
