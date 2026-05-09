package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.security.UserStatusPort;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private final KeycloakAdminClient client;
    private final UserPreferenceService preferenceService;
    private final UserSecurityProperties securityProperties;
    private final UserStatusPort userStatus;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        if (!userStatus.isActive(userSub)) {
            throw new BusinessException("Cannot send password reset to a disabled account");
        }
        syncThemeForEmail(userSub);
        client.sendActionsEmail(
                userSub,
                List.of("UPDATE_PASSWORD"),
                securityProperties.keycloak().frontendClientId(),
                redirectUri,
                securityProperties.passwordReset().linkLifespanSeconds());
    }

    private void syncThemeForEmail(String userSub) {
        try {
            String theme = preferenceService.getOrDefault(userSub).theme().name();
            client.setUserAttribute(userSub, securityProperties.keycloak().themeAttribute(), theme);
        } catch (RuntimeException ex) {
            log.warn("Failed to sync theme to Keycloak before email user={}: {}", userSub, ex.getMessage());
        }
    }
}
