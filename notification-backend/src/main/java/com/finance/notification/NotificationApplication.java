package com.finance.notification;

import com.finance.common.config.AppProperties;
import com.finance.notification.broadcast.service.BroadcastProperties;
import com.finance.notification.config.MarketSessionProperties;
import com.finance.notification.config.NotificationCacheProperties;
import com.finance.notification.config.MessagingProperties;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.config.NotificationKafkaProperties;
import com.finance.notification.config.NotificationStreamProperties;
import com.finance.notification.config.WatchlistManagementProperties;
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

@SpringBootApplication(scanBasePackages = {
        "com.finance.notification",
        "com.finance.common.cache",
        "com.finance.common.config",
        "com.finance.common.exception",
        "com.finance.common.filter.tier"
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
        MarketHoursProperties.class,
        MarketSessionProperties.class,
        MessagingProperties.class,
        NotificationCacheProperties.class,
        NotificationDispatchProperties.class,
        NotificationKafkaProperties.class,
        NotificationStreamProperties.class,
        WatchlistManagementProperties.class
})
@EnableAsync
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        SpringApplication.run(NotificationApplication.class, args);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
