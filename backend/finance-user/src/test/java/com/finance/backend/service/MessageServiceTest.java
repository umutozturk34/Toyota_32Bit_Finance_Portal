package com.finance.backend.service;

import com.finance.backend.dto.MessageResponse;
import com.finance.backend.dto.enums.MessageDirection;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.MessageMapperImpl;
import com.finance.backend.model.Message;
import com.finance.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final String USER_SUB = "user-1";
    private static final String ADMIN_SUB = "admin-1";

    @Mock private MessageRepository repository;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(repository, new MessageMapperImpl());
    }

    @Test
    void shouldPersistUserToAdminMessageWithNullRecipient_whenSending() {
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(1L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        MessageResponse response = service.sendUserToAdmin(USER_SUB, "Help me");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        Message saved = captor.getValue();
        assertThat(saved.getSenderSub()).isEqualTo(USER_SUB);
        assertThat(saved.getRecipientSub()).isNull();
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.USER_TO_ADMIN);
        assertThat(response.body()).isEqualTo("Help me");
    }

    @Test
    void shouldPersistAdminToUserMessageWithRecipient_whenSending() {
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(2L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        MessageResponse response = service.sendAdminToUser(ADMIN_SUB, USER_SUB, "Account flagged");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        Message saved = captor.getValue();
        assertThat(saved.getSenderSub()).isEqualTo(ADMIN_SUB);
        assertThat(saved.getRecipientSub()).isEqualTo(USER_SUB);
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.ADMIN_TO_USER);
        assertThat(response.body()).isEqualTo("Account flagged");
    }

    @Test
    void shouldRejectAdminToUser_whenRecipientBlank() {
        assertThatThrownBy(() -> service.sendAdminToUser(ADMIN_SUB, "  ", "hi"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.sendAdminToUser(ADMIN_SUB, null, "hi"))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReturnPagedInbox_whenFetchingUserInbox() {
        Message m = Message.builder()
                .id(10L).senderSub(ADMIN_SUB).recipientSub(USER_SUB)
                .body("Welcome").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now()).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByRecipientSubOrderBySentAtDesc(USER_SUB, pageable))
                .thenReturn(new PageImpl<>(List.of(m), pageable, 1));

        Page<MessageResponse> page = service.getUserInbox(USER_SUB, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).body()).isEqualTo("Welcome");
    }

    @Test
    void shouldReturnUserToAdminMessages_whenFetchingAdminInbox() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByDirectionOrderBySentAtDesc(MessageDirection.USER_TO_ADMIN, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MessageResponse> page = service.getAdminInbox(0, 20);

        assertThat(page.getContent()).isEmpty();
        verify(repository).findByDirectionOrderBySentAtDesc(MessageDirection.USER_TO_ADMIN, pageable);
    }

    @Test
    void shouldDelegateUnreadCount_toRepository() {
        when(repository.countByRecipientSubAndReadAtIsNull(USER_SUB)).thenReturn(7L);

        long count = service.getUserUnreadCount(USER_SUB);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void shouldDelegateAdminInboxCount_toRepository() {
        when(repository.countByDirection(MessageDirection.USER_TO_ADMIN)).thenReturn(3L);

        long count = service.getAdminInboxCount();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void shouldMarkOwnMessageAsRead_whenRecipientMatches() {
        Message m = Message.builder().id(5L).senderSub(ADMIN_SUB).recipientSub(USER_SUB)
                .body("Hi").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now()).build();
        when(repository.findById(5L)).thenReturn(Optional.of(m));

        service.markRead(5L, USER_SUB);

        assertThat(m.getReadAt()).isNotNull();
        verify(repository).save(m);
    }

    @Test
    void shouldHide404_whenMarkingMessageNotOwnedByCaller() {
        Message m = Message.builder().id(6L).senderSub(ADMIN_SUB).recipientSub("other-user")
                .body("Hi").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now()).build();
        when(repository.findById(6L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.markRead(6L, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotResetReadAt_whenAlreadyRead() {
        LocalDateTime existingReadAt = LocalDateTime.now().minusHours(2);
        Message m = Message.builder().id(7L).senderSub(ADMIN_SUB).recipientSub(USER_SUB)
                .body("Hi").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now().minusHours(3))
                .readAt(existingReadAt).build();
        when(repository.findById(7L)).thenReturn(Optional.of(m));

        service.markRead(7L, USER_SUB);

        assertThat(m.getReadAt()).isEqualTo(existingReadAt);
        verify(repository, never()).save(any());
    }
}
