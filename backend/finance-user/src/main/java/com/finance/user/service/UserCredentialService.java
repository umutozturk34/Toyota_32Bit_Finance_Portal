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

/**
 * Handles credential operations that delegate to Keycloak, currently the password-change flow.
 * Before triggering the update-password action email it pushes the user's theme and locale to
 * Keycloak so the email is themed and localized; disabled accounts are rejected up front.
 */
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

    /** Sends the Keycloak update-password action email (after syncing theme/locale), refusing disabled accounts. */
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

    /** Best-effort push of the user's locale and theme to Keycloak so action emails render correctly; failures are logged, not thrown. */
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

    /** Falls back to the inbound request's locale (constrained to supported languages) when the user has no saved preference. */
    private String resolveRequestLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        String tag = locale != null ? locale.getLanguage() : null;
        if (tag == null || tag.isBlank()) return DEFAULT_LOCALE;
        String lower = tag.toLowerCase(Locale.ROOT);
        return SUPPORTED_LOCALES.contains(lower) ? lower : DEFAULT_LOCALE;
    }
}
