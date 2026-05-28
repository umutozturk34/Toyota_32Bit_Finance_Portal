package com.finance.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Provides the bounded thread pool used to push SSE notifications off the request/transaction
 * thread. Uses a caller-runs rejection policy so bursts apply back-pressure rather than dropping
 * pushes, and drains in-flight tasks on shutdown.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class NotificationAsyncConfig {

    public static final String SSE_EXECUTOR = "sseTaskExecutor";

    private final NotificationAsyncProperties properties;

    @Bean(name = SSE_EXECUTOR)
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.awaitTerminationSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("SSE async executor configured core={} max={} queue={} prefix={}",
                properties.corePoolSize(), properties.maxPoolSize(),
                properties.queueCapacity(), properties.threadNamePrefix());
        return executor;
    }
}
