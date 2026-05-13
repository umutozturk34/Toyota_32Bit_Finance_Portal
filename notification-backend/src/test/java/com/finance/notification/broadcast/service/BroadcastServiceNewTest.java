package com.finance.notification.broadcast.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.broadcast.dto.BroadcastResult;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationDispatcher.BatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceNewTest {

    @Mock private RecipientDirectory recipientDirectory;
    @Mock private NotificationDispatcher dispatcher;

    private BroadcastService service;

    @BeforeEach
    void setUp() {
        BroadcastProperties properties = new BroadcastProperties(50, 1000);
        service = new BroadcastService(recipientDirectory, dispatcher, properties);
    }

    private Page<String> page(List<String> content, int page, int size, long total) {
        return new PageImpl<>(content, Pageable.ofSize(size).withPage(page), total);
    }

    @Test
    void broadcast_dispatchesToSinglePage_andSkipsSelf() {
        when(recipientDirectory.count()).thenReturn(3L);
        when(recipientDirectory.findUserSubs(any(Pageable.class)))
                .thenReturn(page(List.of("admin", "u1", "u2"), 0, 50, 3));
        when(dispatcher.dispatchBatched(any())).thenReturn(new BatchResult(2, 0));

        BroadcastResult result = service.broadcast("admin",
                new BroadcastRequest("title", "body"));

        assertThat(result.dispatched()).isEqualTo(2);
        assertThat(result.totalRecipients()).isEqualTo(3);
    }

    @Test
    void broadcast_propagatesFailureCounts_fromDispatcher() {
        when(recipientDirectory.count()).thenReturn(2L);
        when(recipientDirectory.findUserSubs(any(Pageable.class)))
                .thenReturn(page(List.of("u1", "u2"), 0, 50, 2));
        when(dispatcher.dispatchBatched(any())).thenReturn(new BatchResult(1, 1));

        BroadcastResult result = service.broadcast("admin",
                new BroadcastRequest("title", "body"));

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void broadcast_raisesBadRequest_whenRecipientCountExceedsMax() {
        BroadcastProperties tight = new BroadcastProperties(50, 1);
        BroadcastService tightService = new BroadcastService(recipientDirectory, dispatcher, tight);
        when(recipientDirectory.count()).thenReturn(5L);

        assertThatThrownBy(() -> tightService.broadcast("admin",
                new BroadcastRequest("t", "b")))
                .isInstanceOf(BadRequestException.class);
        verify(dispatcher, never()).dispatchBatched(any());
    }

    @Test
    void broadcast_skipsDispatchEntirely_whenEveryRecipientIsAdmin() {
        when(recipientDirectory.count()).thenReturn(1L);
        when(recipientDirectory.findUserSubs(any(Pageable.class)))
                .thenReturn(page(List.of("admin"), 0, 50, 1));

        BroadcastResult result = service.broadcast("admin",
                new BroadcastRequest("t", "b"));

        assertThat(result.dispatched()).isZero();
        verify(dispatcher, never()).dispatchBatched(any());
    }
}
