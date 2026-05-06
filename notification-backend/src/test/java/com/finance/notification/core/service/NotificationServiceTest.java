package com.finance.notification.core.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationMapper mapper;

    @InjectMocks
    private NotificationService service;

    private Notification owned;

    @BeforeEach
    void setUp() {
        owned = Notification.create("user-1", NotificationType.SYSTEM, "t", "b", Map.of(), null);
    }

    @Test
    void list_returnsAllPagedWhenUnreadOnlyFalse() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> page = new PageImpl<>(List.of(owned), pageable, 1L);
        when(repository.findByUserSubOrderByCreatedAtDesc("user-1", pageable)).thenReturn(page);
        when(mapper.toResponse(owned)).thenReturn(stubResponse());

        Page<NotificationResponse> result = service.list("user-1", 0, 20, false, null);

        assertThat(result.getContent()).hasSize(1);
        verify(repository, never()).findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    void list_returnsUnreadOnlyWhenFlagTrue() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> page = new PageImpl<>(List.of(owned), pageable, 1L);
        when(repository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc("user-1", pageable)).thenReturn(page);
        when(mapper.toResponse(owned)).thenReturn(stubResponse());

        Page<NotificationResponse> result = service.list("user-1", 0, 20, true, null);

        assertThat(result.getContent()).hasSize(1);
        verify(repository, never()).findByUserSubOrderByCreatedAtDesc(anyString(), any());
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(repository.countByUserSubAndReadAtIsNull("user-1")).thenReturn(5L);

        long result = service.unreadCount("user-1");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void markRead_marksAndPersistsForOwner() {
        when(repository.findById(1L)).thenReturn(Optional.of(owned));
        when(repository.save(owned)).thenReturn(owned);
        when(mapper.toResponse(owned)).thenReturn(stubResponse());

        service.markRead(1L, "user-1");

        assertThat(owned.getReadAt()).isNotNull();
        verify(repository).save(owned);
    }

    @Test
    void markRead_throwsResourceNotFoundForOtherOwner() {
        when(repository.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.markRead(1L, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void markRead_throwsResourceNotFoundForMissingId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllRead_passesUserSubAndCurrentTimestamp() {
        when(repository.markAllRead(eq("user-1"), any(LocalDateTime.class))).thenReturn(3);

        int result = service.markAllRead("user-1");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository, times(1)).markAllRead(eq("user-1"), captor.capture());
        assertThat(result).isEqualTo(3);
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void delete_removesNotificationForOwner() {
        when(repository.findById(1L)).thenReturn(Optional.of(owned));

        service.delete(1L, "user-1");

        verify(repository).delete(owned);
    }

    @Test
    void delete_throwsResourceNotFoundForOtherOwner() {
        when(repository.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.delete(1L, "intruder"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).delete(any(Notification.class));
    }

    private NotificationResponse stubResponse() {
        return new NotificationResponse(1L, NotificationType.SYSTEM, "t", "b",
                Map.of(), null, null, LocalDateTime.now());
    }
}
