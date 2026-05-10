package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.security.UserStatusPort;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("tr", "en");
    private static final String DEFAULT_LOCALE = "en";

    private final KeycloakAdminClient client;
    private final UserPreferenceService preferenceService;
    private final UserSecurityProperties securityProperties;
    private final UserStatusPort userStatus;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        if (!userStatus.isActive(userSub)) {
            throw new BusinessException("error.credential.disabledAccount");
        }
        syncPreferencesForEmail(userSub);
        client.sendActionsEmail(
                userSub,
                List.of("UPDATE_PASSWORD"),
                securityProperties.keycloak().frontendClientId(),
                redirectUri,
                securityProperties.passwordReset().linkLifespanSeconds());
    }

    private void syncPreferencesForEmail(String userSub) {
        try {
            var persisted = preferenceService.findPersisted(userSub);
            String language = persisted.map(p -> p.language()).orElseGet(this::resolveRequestLocale);
            String theme = persisted.map(p -> p.theme().name()).orElse("DARK");
            if (language != null) {
                client.setUserAttribute(userSub, securityProperties.keycloak().localeAttribute(), language);
            }
            client.setUserAttribute(userSub, securityProperties.keycloak().themeAttribute(), theme);
        } catch (RuntimeException ex) {
            log.warn("Failed to sync preferences to Keycloak before email user={}: {}", userSub, ex.getMessage());
        }
    }

    private String resolveRequestLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        String tag = locale != null ? locale.getLanguage() : null;
        if (tag == null || tag.isBlank()) return DEFAULT_LOCALE;
        String lower = tag.toLowerCase(Locale.ROOT);
        return SUPPORTED_LOCALES.contains(lower) ? lower : DEFAULT_LOCALE;
    }
}
