package com.finance.notification.user;

import com.finance.common.event.UserRegisteredEvent;
import com.finance.notification.core.service.NotificationPreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

    @Mock private NotificationPreferenceService preferenceService;
    @Mock private Acknowledgment ack;

    private UserRegisteredListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserRegisteredListener(preferenceService);
    }

    private UserRegisteredEvent event() {
        return new UserRegisteredEvent("evt-1", "user-1", OffsetDateTime.now());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void should_ensureDefaultsAndAcknowledge_when_userRegistered(boolean created) {
        when(preferenceService.ensureDefaultsExist("user-1")).thenReturn(created);

        listener.onUserRegistered(event(), ack);

        verify(preferenceService).ensureDefaultsExist("user-1");
        verify(ack).acknowledge();
    }
}
