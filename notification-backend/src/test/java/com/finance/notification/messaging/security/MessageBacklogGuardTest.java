package com.finance.notification.messaging.security;

import com.finance.notification.config.MessagingProperties;
import com.finance.notification.messaging.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageBacklogGuardTest {

    private static final String USER = "user-1";
    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    @Mock private MessageRepository repository;

    private MessageBacklogGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MessageBacklogGuard(repository, new MessagingProperties(2, 5, 60));
    }

    @Test
    void wouldExceedBacklog_returnsFalse_whenNoUnansweredMessages() {
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.empty());
        when(repository.countUserToAdminSince(eq(USER), any(LocalDateTime.class))).thenReturn(0L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isFalse();
    }

    @Test
    void wouldExceedBacklog_returnsFalse_whenBelowLimit() {
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.empty());
        when(repository.countUserToAdminSince(USER, EPOCH)).thenReturn(1L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isFalse();
    }

    @Test
    void wouldExceedBacklog_returnsTrue_whenAtLimit() {
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.empty());
        when(repository.countUserToAdminSince(USER, EPOCH)).thenReturn(2L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isTrue();
    }

    @Test
    void wouldExceedBacklog_returnsTrue_whenAboveLimit() {
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.empty());
        when(repository.countUserToAdminSince(USER, EPOCH)).thenReturn(5L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isTrue();
    }

    @Test
    void wouldExceedBacklog_countsOnlyMessagesAfterAdminReply_whenAdminReplyExists() {
        LocalDateTime adminReplyAt = LocalDateTime.of(2026, 5, 9, 14, 0);
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.of(adminReplyAt));
        when(repository.countUserToAdminSince(USER, adminReplyAt)).thenReturn(1L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isFalse();
    }

    @Test
    void wouldExceedBacklog_resetsCounter_whenAdminRepliedAfterPreviousBacklog() {
        LocalDateTime adminReplyAt = LocalDateTime.of(2026, 5, 9, 14, 0);
        when(repository.findLastAdminReplyAt(USER)).thenReturn(Optional.of(adminReplyAt));
        when(repository.countUserToAdminSince(USER, adminReplyAt)).thenReturn(0L);

        boolean result = guard.wouldExceedBacklog(USER);

        assertThat(result).isFalse();
    }

    @Test
    void maxUnanswered_returnsConfiguredValue() {
        int max = guard.maxUnanswered();

        assertThat(max).isEqualTo(2);
    }
}
