package com.finance.notification.broadcast.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.broadcast.dto.BroadcastResult;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private RecipientDirectory recipientDirectory;
    @Mock private NotificationDispatcher dispatcher;

    private BroadcastService service;

    @BeforeEach
    void setUp() {
        BroadcastProperties properties = new BroadcastProperties(2, 100);
        service = new BroadcastService(recipientDirectory, dispatcher, properties);
    }

    @Test
    void should_paginateAndDispatchToEveryRecipient_when_multiplePagesExist() {
        when(recipientDirectory.count()).thenReturn(3L);
        when(recipientDirectory.findUserSubs(any(Pageable.class)))
                .thenReturn(pageOf(List.of("sub-1", "sub-2"), 0, 3))
                .thenReturn(pageOf(List.of("sub-3"), 1, 3));

        BroadcastResult result = service.broadcast("admin-1",
                new BroadcastRequest("Bakim", "Yarin 02:00"));

        assertThat(result.totalRecipients()).isEqualTo(3);
        assertThat(result.dispatched()).isEqualTo(3);
        assertThat(result.failed()).isZero();
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(3)).dispatch(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationRequest::userSub)
                .containsExactly("sub-1", "sub-2", "sub-3");
        captor.getAllValues().forEach(req -> {
            assertThat(req.type()).isEqualTo(NotificationType.SYSTEM);
            assertThat(req.payload()).isInstanceOf(SystemPayload.class);
            SystemPayload payload = (SystemPayload) req.payload();
            assertThat(payload.title()).isEqualTo("Bakim");
            assertThat(payload.issuedBy()).isEqualTo("admin-1");
        });
    }

    @Test
    void should_keepDispatchingOthers_when_oneRecipientDispatchFails() {
        when(recipientDirectory.count()).thenReturn(2L);
        when(recipientDirectory.findUserSubs(any(Pageable.class)))
                .thenReturn(pageOf(List.of("sub-fail", "sub-ok"), 0, 2));
        doThrow(new RuntimeException("smtp down"))
                .when(dispatcher).dispatch(argThat(matchSub("sub-fail")));

        BroadcastResult result = service.broadcast("admin",
                new BroadcastRequest("title", "body"));

        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.totalRecipients()).isEqualTo(2);
    }

    @Test
    void should_throwBadRequest_when_recipientCountExceedsMax() {
        BroadcastProperties tightProperties = new BroadcastProperties(2, 1);
        BroadcastService tightService = new BroadcastService(recipientDirectory, dispatcher, tightProperties);
        when(recipientDirectory.count()).thenReturn(5L);

        assertThatThrownBy(() -> tightService.broadcast("admin",
                new BroadcastRequest("t", "b")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds limit");
        verify(dispatcher, times(0)).dispatch(any());
    }

    private static Page<String> pageOf(List<String> content, int page, long total) {
        return new PageImpl<>(content, Pageable.ofSize(2).withPage(page), total);
    }

    private static ArgumentMatcher<NotificationRequest> matchSub(String userSub) {
        Predicate<NotificationRequest> predicate = req -> userSub.equals(req.userSub());
        return predicate::test;
    }
}
