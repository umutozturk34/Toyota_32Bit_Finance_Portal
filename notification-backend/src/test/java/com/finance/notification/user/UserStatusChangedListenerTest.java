package com.finance.notification.user;

import com.finance.common.event.UserStatusChangedEvent;
import com.finance.common.security.UserStatusPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserStatusChangedListenerTest {

    @Mock private UserStatusPort userStatus;
    @Mock private Acknowledgment ack;

    @InjectMocks
    private UserStatusChangedListener listener;

    @Test
    void should_invalidateLocalCache_when_eventConsumed() {
        UserStatusChangedEvent event = new UserStatusChangedEvent(
                "evt-1", OffsetDateTime.now(), "user-1", false);

        listener.onStatusChanged(event, ack);

        verify(userStatus).invalidate("user-1");
        verify(ack).acknowledge();
    }

    @Test
    void should_invalidateOnUnbanToo_when_enabledTrue() {
        UserStatusChangedEvent event = new UserStatusChangedEvent(
                "evt-2", OffsetDateTime.now(), "user-2", true);

        listener.onStatusChanged(event, ack);

        verify(userStatus).invalidate("user-2");
        verify(ack).acknowledge();
    }
}
