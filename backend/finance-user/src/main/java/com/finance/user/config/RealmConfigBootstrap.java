package com.finance.user.config;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class RealmConfigBootstrap {

    private static final String FRONTEND_CLIENT_ID = "finance-frontend";
    private static final List<String> MOBILE_REDIRECT_URIS = List.of(
            "financeportal://*",
            "exp://*",
            "exp://*/--/*",
            "https://auth.expo.io/*"
    );

    private final KeycloakAdminClient client;

    @Bean
    public ApplicationRunner ensureRealmEditableUsername() {
        return args -> {
            try {
                client.ensureRealmFlag("editUsernameAllowed", true);
            } catch (Exception e) {
                log.warn("Could not ensure Keycloak realm flag editUsernameAllowed=true: {}", e.getMessage());
            }
        };
    }

    @Bean
    public ApplicationRunner ensureMobileRedirectUris() {
        return args -> {
            try {
                client.ensureClientRedirectUris(FRONTEND_CLIENT_ID, MOBILE_REDIRECT_URIS);
            } catch (Exception e) {
                log.warn("Could not ensure mobile redirect URIs on {}: {}", FRONTEND_CLIENT_ID, e.getMessage());
            }
        };
    }
}
