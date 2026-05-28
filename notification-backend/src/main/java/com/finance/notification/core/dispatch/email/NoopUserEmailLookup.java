package com.finance.notification.core.dispatch.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * Fallback {@link UserEmailLookup} that resolves no addresses, registered only when no other
 * implementation (e.g. the Keycloak one) is present, so the email channel simply stays disabled.
 */
@Configuration
public class NoopUserEmailLookup {

    @Bean
    @ConditionalOnMissingBean(UserEmailLookup.class)
    public UserEmailLookup userEmailLookup() {
        return userSub -> Optional.empty();
    }
}
