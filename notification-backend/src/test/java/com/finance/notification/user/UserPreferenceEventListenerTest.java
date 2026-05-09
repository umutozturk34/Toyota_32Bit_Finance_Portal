package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import com.finance.notification.user.UserPreferenceCacheService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPreferenceEventListenerTest {

    private static final String USER_SUB = "user-sub";

    @Mock private UserPreferenceCacheService cacheService;
    @Mock private Acknowledgment ack;

    private UserPreferenceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserPreferenceEventListener(cacheService, Caffeine.newBuilder().build());
    }

    @Test
    void should_upsertCacheAndAcknowledge_when_eventIsNew() {
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "UTC", "1M", "DAILY", true);

        listener.onUserPreferencesUpdated(event, ack);

        verify(cacheService).upsertFromEvent(event);
        verify(ack).acknowledge();
    }

    @Test
    void should_skipCacheButStillAcknowledge_when_eventAlreadyProcessed() {
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "UTC", "1M", "DAILY", true);

        listener.onUserPreferencesUpdated(event, ack);
        listener.onUserPreferencesUpdated(event, ack);

        verify(cacheService).upsertFromEvent(event);
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }
}
