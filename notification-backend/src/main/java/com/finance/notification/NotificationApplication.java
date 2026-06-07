package com.finance.notification;

import com.finance.common.config.AppProperties;
import com.finance.notification.broadcast.service.BroadcastProperties;
import com.finance.notification.config.MarketSessionProperties;
import com.finance.notification.config.NotificationAsyncProperties;
import com.finance.notification.config.NotificationCacheProperties;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.common.event.KafkaTopicsProperties;
import com.finance.notification.config.NotificationKafkaProperties;
import com.finance.notification.config.NotificationKeycloakProperties;
import com.finance.notification.config.NotificationOutboxProperties;
import com.finance.notification.config.NotificationStreamProperties;
import com.finance.notification.config.PdfExportProperties;
import com.finance.notification.config.PriceAlertProperties;
import com.finance.notification.config.WatchlistManagementProperties;
import com.finance.notification.core.dispatch.slot.SlotProperties;
import com.finance.notification.market.MarketHoursProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.util.TimeZone;

/**
 * Spring Boot entry point for the standalone notification microservice. Component-scans this module
 * plus the shared finance-common infrastructure, enables async and scheduled execution, and pins the
 * default time zone to Europe/Istanbul so market sessions and outbox cadences use exchange-local time.
 */
@SpringBootApplication(scanBasePackages = {
        "com.finance.notification",
        "com.finance.common.cache",
        "com.finance.common.config",
        "com.finance.common.exception",
        "com.finance.common.filter",
        "com.finance.common.i18n",
        "com.finance.common.security"
})
@EntityScan(basePackages = {
        "com.finance.notification",
        "com.finance.common.model"
})
@EnableJpaRepositories(basePackages = {
        "com.finance.notification",
        "com.finance.common.repository"
})
@EnableConfigurationProperties({
        AppProperties.class,
        BroadcastProperties.class,
        KafkaTopicsProperties.class,
        com.finance.common.event.KafkaAdminProperties.class,
        MarketHoursProperties.class,
        MarketSessionProperties.class,
        NotificationAsyncProperties.class,
        NotificationCacheProperties.class,
        NotificationDispatchProperties.class,
        NotificationKafkaProperties.class,
        NotificationKeycloakProperties.class,
        NotificationOutboxProperties.class,
        NotificationStreamProperties.class,
        PdfExportProperties.class,
        SlotProperties.class,
        PriceAlertProperties.class,
        WatchlistManagementProperties.class
})
@EnableAsync
@EnableScheduling
public class NotificationApplication {

    /**
     * Boots the microservice. Sets the JVM-wide default time zone to Europe/Istanbul before the
     * Spring context starts so all date/time handling (market sessions, outbox cadences) runs in
     * exchange-local time regardless of host configuration.
     */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        SpringApplication.run(NotificationApplication.class, args);
    }

    /**
     * System clock bound to the default zone (pinned to Europe/Istanbul in {@link #main}). Injected
     * wherever "now" is needed so time-dependent logic can be substituted with a fixed clock in tests.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
