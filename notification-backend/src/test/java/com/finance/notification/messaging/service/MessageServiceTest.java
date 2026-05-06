package com.finance.notification.messaging.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.messaging.dispatch.AdminInboxEvent;
import com.finance.notification.messaging.dispatch.MessageDispatchEvent;
import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.model.Message;
import com.finance.notification.messaging.model.MessageDirection;
import com.finance.notification.messaging.repository.ClosedConversationRepository;
import com.finance.notification.messaging.repository.MessageRepository;
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
    @Mock private ClosedConversationRepository closedRepository;
    @Mock private com.finance.notification.messaging.security.MessageDuplicateGuard duplicateGuard;
    @Mock private com.finance.notification.messaging.security.MessageCooldownGuard cooldownGuard;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @Mock private com.finance.notification.core.dispatch.KeycloakUserEmailLookup userDirectory;
    @Mock private com.finance.notification.messaging.presence.ActiveConversationRegistry presence;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(repository, closedRepository, new MessageMapperImpl(),
                duplicateGuard, cooldownGuard, events, userDirectory, presence);
    }

    @Test
    void should_persistUserToAdminWithNullRecipient_when_sending() {
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
    void should_persistAdminToUserWithRecipient_when_sending() {
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
    void should_rejectAdminToUser_when_recipientBlankOrNull() {
        assertThatThrownBy(() -> service.sendAdminToUser(ADMIN_SUB, "  ", "hi"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.sendAdminToUser(ADMIN_SUB, null, "hi"))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_returnPagedInbox_when_fetchingUserInbox() {
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
    void should_returnUserToAdminMessages_when_fetchingAdminInbox() {
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByDirectionOrderBySentAtDesc(MessageDirection.USER_TO_ADMIN, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MessageResponse> page = service.getAdminInbox(0, 20);

        assertThat(page.getContent()).isEmpty();
        verify(repository).findByDirectionOrderBySentAtDesc(MessageDirection.USER_TO_ADMIN, pageable);
    }

    @Test
    void should_delegateUnreadCount_when_calledForUser() {
        when(repository.countByRecipientSubAndReadAtIsNull(USER_SUB)).thenReturn(7L);

        long count = service.getUserUnreadCount(USER_SUB);

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void should_delegateAdminInboxCount_when_calledForAdmin() {
        when(repository.countByDirection(MessageDirection.USER_TO_ADMIN)).thenReturn(3L);

        long count = service.getAdminInboxCount();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void should_markOwnMessageAsRead_when_recipientMatches() {
        Message m = Message.builder().id(5L).senderSub(ADMIN_SUB).recipientSub(USER_SUB)
                .body("Hi").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now()).build();
        when(repository.findById(5L)).thenReturn(Optional.of(m));

        service.markRead(5L, USER_SUB);

        assertThat(m.getReadAt()).isNotNull();
        verify(repository).save(m);
    }

    @Test
    void should_hide404_when_markingMessageNotOwnedByCaller() {
        Message m = Message.builder().id(6L).senderSub(ADMIN_SUB).recipientSub("other-user")
                .body("Hi").direction(MessageDirection.ADMIN_TO_USER)
                .sentAt(LocalDateTime.now()).build();
        when(repository.findById(6L)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.markRead(6L, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_notResetReadAt_when_alreadyRead() {
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

    @Test
    void should_rejectUserToAdmin_when_duplicateBodyDetected() {
        when(duplicateGuard.isDuplicate(USER_SUB, "spam")).thenReturn(true);

        assertThatThrownBy(() -> service.sendUserToAdmin(USER_SUB, "spam"))
                .isInstanceOf(BadRequestException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void should_publishDispatchEvent_when_adminMessageSaved() {
        when(duplicateGuard.isDuplicate(ADMIN_SUB, "Hi")).thenReturn(false);
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(9L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        service.sendAdminToUser(ADMIN_SUB, USER_SUB, "Hi");

        ArgumentCaptor<MessageDispatchEvent> captor = ArgumentCaptor.forClass(MessageDispatchEvent.class);
        verify(events).publishEvent(captor.capture());
        MessageDispatchEvent event = captor.getValue();
        assertThat(event.recipientSub()).isEqualTo(USER_SUB);
        assertThat(event.senderSub()).isEqualTo(ADMIN_SUB);
        assertThat(event.body()).isEqualTo("Hi");
    }

    @Test
    void should_publishAdminInboxEvent_when_userToAdminSent() {
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(11L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        service.sendUserToAdmin(USER_SUB, "Help");

        ArgumentCaptor<AdminInboxEvent> captor = ArgumentCaptor.forClass(AdminInboxEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().message().senderSub()).isEqualTo(USER_SUB);
    }

    @Test
    void should_skipAdminInboxEventAndStampReadAt_when_anyAdminViewingUserThread() {
        when(presence.isAnyoneActiveOn("user:" + USER_SUB)).thenReturn(true);
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(12L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        service.sendUserToAdmin(USER_SUB, "Hi while admin viewing");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReadAt()).isNotNull();
        verify(events, never()).publishEvent(any(AdminInboxEvent.class));
    }

    @Test
    void should_skipMessageDispatchEventAndStampReadAt_when_userViewingAdminThread() {
        when(presence.getActiveKey(USER_SUB)).thenReturn(Optional.of("admin"));
        when(repository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(13L);
            m.setSentAt(LocalDateTime.now());
            return m;
        });

        service.sendAdminToUser(ADMIN_SUB, USER_SUB, "Hi while user viewing");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReadAt()).isNotNull();
        verify(events, never()).publishEvent(any(MessageDispatchEvent.class));
    }
}
