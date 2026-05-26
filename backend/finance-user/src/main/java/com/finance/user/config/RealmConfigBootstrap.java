package com.finance.user.config;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
public class RealmConfigBootstrap {

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
}
