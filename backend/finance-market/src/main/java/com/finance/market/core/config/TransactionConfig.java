package com.finance.market.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Configuration exposing a {@link TransactionTemplate} for programmatic
 * (imperative) transaction management, complementing declarative
 * {@code @Transactional} usage.
 */
@Configuration
public class TransactionConfig {

    /**
     * Provides a {@link TransactionTemplate} bound to the application's
     * {@link PlatformTransactionManager} for code that needs explicit
     * transaction control.
     *
     * @param transactionManager the platform transaction manager
     * @return a ready-to-use transaction template
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
