package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final long PASSWORD_LINK_LIFESPAN_SECONDS = 300L;
    private static final String FRONTEND_CLIENT_ID = "finance-frontend";
    private static final String THEME_ATTRIBUTE = "themePreference";

    private final KeycloakAdminClient client;
    private final UserPreferenceService preferenceService;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        syncThemeForEmail(userSub);
        client.sendActionsEmail(userSub, List.of("UPDATE_PASSWORD"), FRONTEND_CLIENT_ID, redirectUri, PASSWORD_LINK_LIFESPAN_SECONDS);
    }

    private void syncThemeForEmail(String userSub) {
        try {
            String theme = preferenceService.getOrDefault(userSub).theme().name();
            client.setUserAttribute(userSub, THEME_ATTRIBUTE, theme);
        } catch (RuntimeException ex) {
            log.warn("Failed to sync theme to Keycloak before email user={}: {}", userSub, ex.getMessage());
        }
    }
}
