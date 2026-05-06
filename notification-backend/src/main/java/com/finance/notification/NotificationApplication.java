package com.finance.notification;

import com.finance.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = {
        "com.finance.notification",
        "com.finance.common.cache",
        "com.finance.common.config",
        "com.finance.common.exception",
        "com.finance.common.filter.tier"
})
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class NotificationApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        SpringApplication.run(NotificationApplication.class, args);
    }
}
