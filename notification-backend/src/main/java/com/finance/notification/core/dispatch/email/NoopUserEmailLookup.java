package com.finance.notification.core.dispatch.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@Configuration
public class NoopUserEmailLookup {

    @Bean
    @ConditionalOnMissingBean(UserEmailLookup.class)
    public UserEmailLookup userEmailLookup() {
        return userSub -> Optional.empty();
    }
}
