package com.finance.notification.core.mail;

import com.finance.notification.config.NotificationOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Scheduled housekeeping that purges successfully SENT outbox rows older than the configured
 * retention, keeping the outbox table from growing unbounded.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class EmailOutboxCleanupScheduler {

    private static final String DELETE_OLD_SENT_SQL = """
            DELETE FROM email_outbox
            WHERE status = 'SENT'
              AND sent_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxProperties properties;

    @Scheduled(cron = "${notification.email.outbox.cleanup-cron}", zone = "Europe/Istanbul")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeOldSent() {
        Timestamp threshold = Timestamp.valueOf(LocalDateTime.now().minus(properties.sentRetention()));
        int deleted = jdbcTemplate.update(DELETE_OLD_SENT_SQL, threshold);
        if (deleted > 0) {
            log.info("Email outbox cleanup purged sentRows={} retentionHours={}",
                    deleted, properties.sentRetention().toHours());
        }
    }
}
