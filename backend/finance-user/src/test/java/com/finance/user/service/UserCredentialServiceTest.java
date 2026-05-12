package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.security.UserStatusPort;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.enums.ThemePreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialServiceTest {

    private static final String USER = "kc-user-1";
    private static final String REDIRECT = "https://app.local/profile";
    private static final String FRONTEND_CLIENT = "finance-frontend";
    private static final String LOCALE_ATTR = "locale";
    private static final String THEME_ATTR = "themePreference";
    private static final long LINK_LIFESPAN = 300L;

    @Mock private KeycloakAdminClient client;
    @Mock private UserPreferenceService preferenceService;
    @Mock private UserStatusPort userStatus;

    private UserCredentialService service;

    @BeforeEach
    void setUp() {
        UserSecurityProperties props = new UserSecurityProperties(
                new UserSecurityProperties.EmailChange(3, 6, Duration.ofMinutes(10)),
                new UserSecurityProperties.PasswordReset(LINK_LIFESPAN),
                new UserSecurityProperties.Keycloak(FRONTEND_CLIENT, THEME_ATTR, LOCALE_ATTR));
        service = new UserCredentialService(client, preferenceService, props, userStatus);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void initiatePasswordChange_throwsBusinessException_whenAccountInactive() {
        when(userStatus.isActive(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.initiatePasswordChange(USER, REDIRECT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.credential.disabledAccount");
        verify(client, never()).sendActionsEmail(any(), any(), any(), any(), anyLong());
    }

    @Test
    void initiatePasswordChange_usesPersistedPreferences_whenAvailable() {
        when(userStatus.isActive(USER)).thenReturn(true);
        when(preferenceService.findPersisted(USER)).thenReturn(Optional.of(
                new UserPreferenceResponse(USER, ThemePreference.LIGHT, "tr",
                        "Europe/Istanbul", "1M", true)));

        service.initiatePasswordChange(USER, REDIRECT);

        verify(client).setUserAttribute(USER, LOCALE_ATTR, "tr");
        verify(client).setUserAttribute(USER, THEME_ATTR, "LIGHT");
        verify(client).sendActionsEmail(USER, List.of("UPDATE_PASSWORD"),
                FRONTEND_CLIENT, REDIRECT, LINK_LIFESPAN);
    }

    @Test
    void initiatePasswordChange_fallsBackToDefaultDarkTheme_whenNoPreferences() {
        LocaleContextHolder.setLocale(new Locale("tr"));
        when(userStatus.isActive(USER)).thenReturn(true);
        when(preferenceService.findPersisted(USER)).thenReturn(Optional.empty());

        service.initiatePasswordChange(USER, REDIRECT);

        verify(client).setUserAttribute(USER, LOCALE_ATTR, "tr");
        verify(client).setUserAttribute(USER, THEME_ATTR, "DARK");
        verify(client).sendActionsEmail(USER, List.of("UPDATE_PASSWORD"),
                FRONTEND_CLIENT, REDIRECT, LINK_LIFESPAN);
    }

    @ParameterizedTest
    @CsvSource({
            "tr, tr",
            "en, en",
            "TR, tr",
            "fr, en",
            "de, en"
    })
    void initiatePasswordChange_resolvesRequestLocale_whenNoPreferences(String requestLang, String expectedLocale) {
        LocaleContextHolder.setLocale(new Locale(requestLang));
        when(userStatus.isActive(USER)).thenReturn(true);
        when(preferenceService.findPersisted(USER)).thenReturn(Optional.empty());

        service.initiatePasswordChange(USER, REDIRECT);

        verify(client).setUserAttribute(USER, LOCALE_ATTR, expectedLocale);
    }

    @Test
    void initiatePasswordChange_swallowsExceptionDuringPreferenceSync() {
        when(userStatus.isActive(USER)).thenReturn(true);
        when(preferenceService.findPersisted(USER)).thenThrow(new RuntimeException("kc down"));

        service.initiatePasswordChange(USER, REDIRECT);

        verify(client).sendActionsEmail(USER, List.of("UPDATE_PASSWORD"),
                FRONTEND_CLIENT, REDIRECT, LINK_LIFESPAN);
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static long anyLong() { return org.mockito.ArgumentMatchers.anyLong(); }
}
