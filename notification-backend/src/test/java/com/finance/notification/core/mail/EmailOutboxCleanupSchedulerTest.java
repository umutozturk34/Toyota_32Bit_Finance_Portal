package com.finance.notification.core.mail;

import com.finance.notification.config.NotificationOutboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxCleanupSchedulerTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private EmailOutboxCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        NotificationOutboxProperties props = new NotificationOutboxProperties(
                10, 3, List.of(Duration.ofSeconds(10)),
                Duration.ofMinutes(5), Duration.ofDays(7),
                Duration.ofSeconds(30), Duration.ofMinutes(1),
                "0 0 * * *");
        scheduler = new EmailOutboxCleanupScheduler(jdbcTemplate, props);
    }

    @Test
    void purgeOldSent_logsAndCompletes_whenRowsDeleted() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(5);

        scheduler.purgeOldSent();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class));
    }

    @Test
    void purgeOldSent_isQuiet_whenNoRowsDeleted() {
        when(jdbcTemplate.update(anyString(), any(Timestamp.class))).thenReturn(0);

        scheduler.purgeOldSent();

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class));
    }
}
