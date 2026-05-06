package com.finance.notification.messaging.dispatch;

import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ConversationLifecycleEventListenerTest {

    @Mock private NotificationDispatcher dispatcher;

    private ConversationLifecycleEventListener listener;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        listener = new ConversationLifecycleEventListener(dispatcher);
    }

    @ParameterizedTest
    @CsvSource({
            "CLOSED,Sohbet kapatıldı",
            "REOPENED,Sohbet yeniden açıldı",
            "DELETED,Sohbet silindi"
    })
    void should_dispatchSystemNotificationWithExpectedTitle_when_lifecycleActionFires(
            ConversationLifecycleEvent.Action action, String expectedTitle) {
        listener.onLifecycleChanged(new ConversationLifecycleEvent("user-7", "admin-1", action));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest req = captor.getValue();
        assertThat(req.userSub()).isEqualTo("user-7");
        assertThat(req.type()).isEqualTo(NotificationType.SYSTEM);
        assertThat(req.payload()).isInstanceOf(SystemPayload.class);
        SystemPayload payload = (SystemPayload) req.payload();
        assertThat(payload.title()).isEqualTo(expectedTitle);
        assertThat(payload.body()).isNotBlank();
        assertThat(payload.issuedBy()).isEqualTo("admin-1");
    }

    @Test
    void should_neverInteractWithDispatcher_when_listenerIsConstructedButNotInvoked() {
        verifyNoInteractions(dispatcher);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }
}
