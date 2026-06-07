package com.finance.market.core.config;

import com.finance.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Core application configuration for the market module, providing shared
 * infrastructure beans such as the async task executor.
 */
@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final AppProperties appProperties;

    /**
     * Defines the {@code taskExecutor} backing {@code @Async} work, sized from
     * {@code app.async.*} (core/max pool, queue capacity) with an {@code async-}
     * thread-name prefix for easier diagnostics.
     *
     * @return the initialized thread-pool executor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        AppProperties.Async async = appProperties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
