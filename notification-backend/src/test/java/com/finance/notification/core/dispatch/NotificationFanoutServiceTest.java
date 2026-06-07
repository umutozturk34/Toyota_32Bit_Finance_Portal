package com.finance.notification.core.dispatch;

import com.finance.common.security.UserStatusPort;
import com.finance.notification.config.NotificationDispatchProperties;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.user.UserPreferenceCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the fanout paths the listener-level tests skip: the bulk payload resolver variant, and
 * the page-level prepare failure that must be counted as a per-recipient failure rather than
 * propagated. Collaborators are mocked so only the fanout's paging/aggregation logic is asserted.
 */
@ExtendWith(MockitoExtension.class)
class NotificationFanoutServiceTest {

    @Mock private NotificationDispatcher dispatcher;
    @Mock private NotificationPersister persister;
    @Mock private UserPreferenceCacheService userPreferenceCacheService;
    @Mock private UserStatusPort userStatus;

    private NotificationFanoutService service;

    @BeforeEach
    void setUp() {
        NotificationDispatchProperties properties = new NotificationDispatchProperties(
                null, null, new NotificationDispatchProperties.Fanout(200), null);
        service = new NotificationFanoutService(
                dispatcher, persister, properties, userPreferenceCacheService, userStatus);
    }

    private NotificationPreference subscriber(String userSub) {
        return NotificationPreference.builder().userSub(userSub).build();
    }

    private SystemPayload payload() {
        return new SystemPayload("title", "body", "ops");
    }

    @Test
    void fanoutBulk_dispatchesResolvedPayloads_andPersistsBatch() {
        NotificationPreference pref = subscriber("user-1");
        when(userPreferenceCacheService.loadAll(any())).thenReturn(Map.of());
        when(userStatus.activeStatusOf(any())).thenReturn(Map.of("user-1", true));
        when(dispatcher.prepare(any(NotificationRequest.class), any(), any(), anyBoolean()))
                .thenReturn(Optional.of(new Prepared("user-1", null, null)));

        NotificationFanoutService.FanoutResult result = service.fanoutBulk(
                "system.broadcast",
                page -> singlePage(pref),
                content -> Map.of("user-1", payload()));

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(persister).persistBatch(any());
    }

    @Test
    void fanout_countsFailureWithoutPropagating_whenPrepareThrows() {
        NotificationPreference pref = subscriber("user-1");
        when(userPreferenceCacheService.loadAll(any())).thenReturn(Map.of());
        when(userStatus.activeStatusOf(any())).thenReturn(Map.of("user-1", true));
        when(dispatcher.prepare(any(NotificationRequest.class), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("render boom"));

        NotificationFanoutService.FanoutResult result = service.fanout(
                "system.broadcast",
                page -> singlePage(pref),
                p -> Optional.of(payload()));

        assertThat(result.dispatched()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        verify(persister, never()).persistBatch(any());
    }

    @Test
    void fanoutBulk_skipsRecipientsAbsentFromPayloadMap() {
        NotificationPreference pref = subscriber("user-1");

        NotificationFanoutService.FanoutResult result = service.fanoutBulk(
                "system.broadcast",
                page -> singlePage(pref),
                content -> Map.of());

        assertThat(result.dispatched()).isZero();
        assertThat(result.failed()).isZero();
        verify(dispatcher, never()).prepare(any(), any(), any(), anyBoolean());
        verify(persister, never()).persistBatch(any());
    }

    private Page<NotificationPreference> singlePage(NotificationPreference pref) {
        return new PageImpl<>(List.of(pref), Pageable.unpaged(), 1L);
    }
}
