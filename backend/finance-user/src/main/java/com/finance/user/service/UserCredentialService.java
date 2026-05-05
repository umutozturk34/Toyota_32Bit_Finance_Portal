package com.finance.user.service;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final long EMAIL_LINK_LIFESPAN_SECONDS = 600L;
    private static final String FRONTEND_CLIENT_ID = "finance-frontend";
    private static final String THEME_ATTRIBUTE = "themePreference";

    private final KeycloakAdminClient client;
    private final UserPreferenceService preferenceService;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        syncThemeForEmail(userSub);
        client.sendActionsEmail(userSub, List.of("UPDATE_PASSWORD"), FRONTEND_CLIENT_ID, redirectUri, EMAIL_LINK_LIFESPAN_SECONDS);
    }

    public void initiateEmailChange(String userSub, String newEmail, String redirectUri) {
        syncThemeForEmail(userSub);
        client.setEmail(userSub, newEmail);
        client.sendActionsEmail(userSub, List.of("VERIFY_EMAIL_CODE"), FRONTEND_CLIENT_ID, redirectUri, EMAIL_LINK_LIFESPAN_SECONDS);
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
