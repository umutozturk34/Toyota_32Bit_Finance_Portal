package com.finance.notification.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Backs {@code @Scheduled} with a multi-threaded scheduler. The default scheduler runs a single
 * thread, so the four scheduled jobs (outbox poll/reclaim/cleanup + market-session scan) would
 * serialize and one slow job would head-of-line block the rest; a small pool lets them run
 * concurrently. Bean is named {@code taskScheduler} so Spring's scheduling infrastructure adopts it.
 */
@Log4j2
@Configuration
public class NotificationSchedulingConfig {

    private static final int POOL_SIZE = 4;
    private static final String THREAD_PREFIX = "notif-sched-";

    /** The pooled {@link TaskScheduler} Spring's scheduling infrastructure adopts (by the {@code taskScheduler} name). */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_PREFIX);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        log.info("Notification task scheduler configured poolSize={} prefix={}", POOL_SIZE, THREAD_PREFIX);
        return scheduler;
    }
}
