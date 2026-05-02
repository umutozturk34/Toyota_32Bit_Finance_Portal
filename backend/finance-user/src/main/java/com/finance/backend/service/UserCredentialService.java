package com.finance.backend.service;

import com.finance.backend.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCredentialService {

    private static final long EMAIL_LINK_LIFESPAN_SECONDS = 600L;
    private static final String FRONTEND_CLIENT_ID = "finance-frontend";

    private final KeycloakAdminClient client;

    public void initiatePasswordChange(String userSub, String redirectUri) {
        client.sendActionsEmail(userSub, List.of("UPDATE_PASSWORD"), FRONTEND_CLIENT_ID, redirectUri, EMAIL_LINK_LIFESPAN_SECONDS);
    }
}
