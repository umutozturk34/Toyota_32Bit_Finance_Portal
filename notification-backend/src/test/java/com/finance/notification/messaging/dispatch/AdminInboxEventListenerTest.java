package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.model.MessageDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminInboxEventListenerTest {

    @Mock private NotificationStreamRegistry streamRegistry;

    @InjectMocks private AdminInboxEventListener listener;

    @Test
    void should_pushAdminInboxEventToAdminStream_when_userMessageCommitted() {
        MessageResponse response = new MessageResponse(
                42L, "user-1", null, "hello",
                MessageDirection.USER_TO_ADMIN,
                LocalDateTime.now(), null);

        listener.onUserMessageReachedAdminInbox(new AdminInboxEvent(response));

        verify(streamRegistry).publishToAdmins("admin-inbox", response);
    }
}
