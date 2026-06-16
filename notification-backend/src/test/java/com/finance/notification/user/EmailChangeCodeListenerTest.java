package com.finance.notification.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.i18n.Translator;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.mail.EmailOutbox;
import com.finance.notification.core.mail.EmailOutboxRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeCodeListenerTest {

    @Mock private EmailOutboxRepository emailOutboxRepository;
    @SuppressWarnings("unchecked")
    @Mock private Cache<String, Boolean> processedEventIds;
    @Mock private UserStatusPort userStatus;
    @Mock private UserPreferenceCacheService userPreferenceCacheService;
    @Mock private Translator translator;
    @Mock private Acknowledgment ack;

    private EmailChangeCodeListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailChangeCodeListener(emailOutboxRepository, new ObjectMapper(),
                processedEventIds, userStatus, userPreferenceCacheService, translator);
    }

    private EmailChangeCodeRequestedEvent event() {
        return new EmailChangeCodeRequestedEvent(
                "evt-1", "user-sub-1", "old@example.com", "new@example.com",
                "ABC123", "dark",
                OffsetDateTime.now().plusMinutes(15),
                OffsetDateTime.now());
    }

    @Test
    void onEmailChangeCode_skipsAndAcknowledges_whenEventAlreadyProcessed() {
        EmailChangeCodeRequestedEvent event = event();
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(Boolean.TRUE);

        listener.onEmailChangeCode(event, ack);

        verify(emailOutboxRepository, never()).save(any());
        verify(ack).acknowledge();
    }

    @Test
    void onEmailChangeCode_skipsSave_whenUserInactive() {
        EmailChangeCodeRequestedEvent event = event();
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(userStatus.isActive("user-sub-1")).thenReturn(false);

        listener.onEmailChangeCode(event, ack);

        verify(emailOutboxRepository, never()).save(any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }

    @Test
    void onEmailChangeCode_enqueuesOutboxRow_whenUserActive() {
        EmailChangeCodeRequestedEvent event = event();
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(userStatus.isActive("user-sub-1")).thenReturn(true);
        when(userPreferenceCacheService.resolveLocale("user-sub-1")).thenReturn(Locale.ENGLISH);
        when(translator.translate(eq("email.changeCode.subject"), any(Locale.class))).thenReturn("Subject");

        listener.onEmailChangeCode(event, ack);

        // The verification code must be delivered to the NEW address — the change is only applied once the user
        // proves they can receive mail there (regression: it used to go to the old address).
        ArgumentCaptor<EmailOutbox> rowCaptor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getRecipientEmail()).isEqualTo("new@example.com");
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }
}
