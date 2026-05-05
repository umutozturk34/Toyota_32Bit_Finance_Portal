package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.MessagePayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MessageNotificationListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @InjectMocks
    private MessageNotificationListener listener;

    @Test
    void onMessageDispatch_skipsBroadcastWithNullRecipient() {
        listener.onMessageDispatch(new MessageDispatchEvent(null, "admin", "hi"));

        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void onMessageDispatch_dispatchesToRecipientWithMessageType() {
        listener.onMessageDispatch(new MessageDispatchEvent("user-1", "admin", "merhaba"));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.userSub()).isEqualTo("user-1");
        assertThat(request.type()).isEqualTo(NotificationType.MESSAGE);
        assertThat(request.payload()).isInstanceOf(MessagePayload.class);
        MessagePayload payload = (MessagePayload) request.payload();
        assertThat(payload.senderSub()).isEqualTo("admin");
        assertThat(payload.body()).isEqualTo("merhaba");
    }
}
