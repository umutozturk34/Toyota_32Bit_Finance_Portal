package com.finance.notification.messaging.presence;

import com.finance.notification.messaging.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessagePresenceServiceTest {

    private static final String USER_SUB = "user-1";
    private static final String OTHER_USER_SUB = "user-2";

    @Mock private ActiveConversationRegistry registry;
    @Mock private MessageRepository messages;

    @InjectMocks private MessagePresenceService service;

    @BeforeEach
    void setUp() {
    }

    @Test
    void should_markUserInboxRead_when_userRegistersAdminThread() {
        service.register(USER_SUB, "admin");

        verify(registry).register(USER_SUB, "admin");
        verify(messages, times(1)).markUserInboxRead(eq(USER_SUB), any(LocalDateTime.class));
        verify(messages, never()).markAdminInboxRead(any(), any());
    }

    @Test
    void should_markAdminInboxRead_when_adminRegistersUserThread() {
        service.register("admin-1", "user:" + OTHER_USER_SUB);

        verify(registry).register("admin-1", "user:" + OTHER_USER_SUB);
        verify(messages, times(1)).markAdminInboxRead(eq(OTHER_USER_SUB), any(LocalDateTime.class));
        verify(messages, never()).markUserInboxRead(any(), any());
    }

    @Test
    void should_skipBulkMarkRead_when_keyDoesNotMatchKnownPattern() {
        service.register(USER_SUB, "unknown-key");

        verify(registry).register(USER_SUB, "unknown-key");
        verify(messages, never()).markUserInboxRead(any(), any());
        verify(messages, never()).markAdminInboxRead(any(), any());
    }

    @Test
    void should_delegateUnregister_when_called() {
        service.unregister(USER_SUB);

        verify(registry).unregister(USER_SUB);
        verify(messages, never()).markUserInboxRead(any(), any());
        verify(messages, never()).markAdminInboxRead(any(), any());
    }
}
