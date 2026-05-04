package com.finance.notification.broadcast.service;

import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.user.UserPreferenceCache;
import com.finance.notification.user.UserPreferenceCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private UserPreferenceCacheRepository repository;
    @Mock private NotificationDispatcher dispatcher;

    @InjectMocks
    private BroadcastService service;

    private UserPreferenceCache cacheRow(String sub) {
        return UserPreferenceCache.builder().userSub(sub).build();
    }

    @Test
    void broadcast_dispatchesSystemNotificationToEachKnownUser() {
        when(repository.findAll()).thenReturn(List.of(
                cacheRow("u-1"), cacheRow("u-2"), cacheRow("u-3")));

        int count = service.broadcast("admin-1",
                new BroadcastRequest("Bakım", "Yarın 02:00 bakım var"));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(3)).dispatch(captor.capture());
        assertThat(count).isEqualTo(3);
        assertThat(captor.getAllValues()).extracting(NotificationRequest::userSub)
                .containsExactly("u-1", "u-2", "u-3");
        captor.getAllValues().forEach(req -> {
            assertThat(req.type()).isEqualTo(NotificationType.SYSTEM);
            assertThat(req.data()).containsEntry("title", "Bakım");
            assertThat(req.data()).containsEntry("body", "Yarın 02:00 bakım var");
            assertThat(req.data()).containsEntry("issuedBy", "admin-1");
        });
    }

    @Test
    void broadcast_returnsZeroAndDoesNothingWhenNoUsers() {
        when(repository.findAll()).thenReturn(List.of());

        int count = service.broadcast("admin-1",
                new BroadcastRequest("Hi", "There"));

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(dispatcher);
    }
}
