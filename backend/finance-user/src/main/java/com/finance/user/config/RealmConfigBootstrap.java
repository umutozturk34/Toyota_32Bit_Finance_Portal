package com.finance.user.config;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Startup bootstrap that idempotently reconciles required Keycloak realm/client settings (editable
 * username, mobile deep-link redirect URIs). Each runner swallows failures and logs a warning so a
 * temporarily unreachable Keycloak never blocks application startup.
 */
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

    /** Turns on the realm's {@code editUsernameAllowed} flag so the profile screen can rename users. */
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

    /** Registers the Expo/native deep-link redirect URIs on the frontend client so mobile OAuth callbacks are accepted. */
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
