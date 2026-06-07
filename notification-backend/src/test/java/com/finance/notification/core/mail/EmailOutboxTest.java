package com.finance.notification.core.mail;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxTest {

    @Test
    void should_defaultCreatedAtStatusAndNextAttempt_when_prePersistRunsWithoutValues() {
        EmailOutbox outbox = EmailOutbox.builder()
                .recipientEmail("user@example.com")
                .subject("Subject")
                .templateName("price-alert")
                .theme("dark")
                .locale("en")
                .build();

        outbox.prePersist();

        assertThat(outbox.getCreatedAt()).isNotNull();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutbox.Status.PENDING);
        assertThat(outbox.getNextAttemptAt()).isNotNull();
    }

    @Test
    void should_preserveProvidedStatusAndTimestamps_when_prePersistRunsWithValues() {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime next = LocalDateTime.of(2026, 1, 2, 0, 0);
        EmailOutbox outbox = EmailOutbox.builder()
                .recipientEmail("user@example.com")
                .subject("Subject")
                .templateName("price-alert")
                .theme("dark")
                .locale("en")
                .status(EmailOutbox.Status.RELAYED)
                .createdAt(created)
                .nextAttemptAt(next)
                .build();

        outbox.prePersist();

        assertThat(outbox.getCreatedAt()).isEqualTo(created);
        assertThat(outbox.getStatus()).isEqualTo(EmailOutbox.Status.RELAYED);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(next);
    }
}
