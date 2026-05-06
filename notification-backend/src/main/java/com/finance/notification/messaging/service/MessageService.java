package com.finance.notification.messaging.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.messaging.dispatch.AdminInboxEvent;
import com.finance.notification.messaging.dispatch.MessageDispatchEvent;
import com.finance.notification.messaging.dto.ConversationSummary;
import com.finance.notification.messaging.dto.ConversationThread;
import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.model.ClosedConversation;
import com.finance.notification.messaging.model.Message;
import com.finance.notification.messaging.model.MessageDirection;
import com.finance.notification.messaging.repository.ClosedConversationRepository;
import com.finance.notification.messaging.repository.MessageRepository;
import com.finance.notification.messaging.security.MessageCooldownGuard;
import com.finance.notification.messaging.security.MessageDuplicateGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository repository;
    private final ClosedConversationRepository closedRepository;
    private final MessageMapper mapper;
    private final MessageDuplicateGuard duplicateGuard;
    private final MessageCooldownGuard cooldownGuard;
    private final ApplicationEventPublisher events;

    @Transactional
    public MessageResponse sendUserToAdmin(String senderSub, String body) {
        rejectIfClosed(senderSub);
        rejectIfCoolingDown(senderSub);
        rejectDuplicate(senderSub, body);
        Message saved = repository.save(Message.builder()
                .senderSub(senderSub)
                .recipientSub(null)
                .body(body)
                .direction(MessageDirection.USER_TO_ADMIN)
                .build());
        MessageResponse response = mapper.toResponse(saved);
        events.publishEvent(new AdminInboxEvent(response));
        return response;
    }

    @Transactional
    public MessageResponse sendAdminToUser(String adminSub, String recipientSub, String body) {
        if (recipientSub == null || recipientSub.isBlank()) {
            throw new BusinessException("recipientSub cannot be blank for admin-to-user message");
        }
        rejectDuplicate(adminSub, body);
        Message saved = repository.save(Message.builder()
                .senderSub(adminSub)
                .recipientSub(recipientSub)
                .body(body)
                .direction(MessageDirection.ADMIN_TO_USER)
                .build());
        events.publishEvent(new MessageDispatchEvent(recipientSub, adminSub, body));
        return mapper.toResponse(saved);
    }

    private void rejectDuplicate(String senderSub, String body) {
        if (duplicateGuard.isDuplicate(senderSub, body)) {
            throw new BadRequestException("Aynı mesajı kısa süre içinde tekrar gönderdin");
        }
    }

    private void rejectIfCoolingDown(String senderSub) {
        if (cooldownGuard.isCoolingDown(senderSub)) {
            throw new BadRequestException("Çok sık mesaj gönderiyorsun, lütfen birkaç saniye bekle");
        }
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getUserInbox(String userSub, int page, int size) {
        return repository
                .findByRecipientSubOrderBySentAtDesc(userSub, PageRequest.of(page, size))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getUserSent(String userSub, int page, int size) {
        return repository
                .findBySenderSubOrderBySentAtDesc(userSub, PageRequest.of(page, size))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getAdminInbox(int page, int size) {
        return repository
                .findByDirectionOrderBySentAtDesc(MessageDirection.USER_TO_ADMIN, PageRequest.of(page, size))
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUserUnreadCount(String userSub) {
        return repository.countByRecipientSubAndReadAtIsNull(userSub);
    }

    @Transactional(readOnly = true)
    public long getAdminInboxCount() {
        return repository.countByDirection(MessageDirection.USER_TO_ADMIN);
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummary> listConversations(int page, int size) {
        return repository.findConversationSummaries(PageRequest.of(page, size))
                .map(p -> new ConversationSummary(
                        p.getUserSub(),
                        p.getLastBody(),
                        p.getLastSentAt(),
                        Boolean.TRUE.equals(p.getClosed())));
    }

    @Transactional
    public void markRead(Long messageId, String userSub) {
        Message message = repository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        if (!userSub.equals(message.getRecipientSub())) {
            throw new ResourceNotFoundException("Message not found: " + messageId);
        }
        if (message.getReadAt() == null) {
            message.setReadAt(LocalDateTime.now());
            repository.save(message);
        }
    }

    @Transactional(readOnly = true)
    public ConversationThread getConversation(String userSub) {
        List<MessageResponse> messages = repository.findConversation(userSub).stream()
                .map(mapper::toResponse)
                .toList();
        ClosedConversation closed = closedRepository.findById(userSub).orElse(null);
        return new ConversationThread(
                userSub,
                closed != null,
                closed != null ? closed.getClosedAt() : null,
                messages);
    }

    @Transactional
    public void closeConversation(String userSub, String adminSub) {
        if (closedRepository.existsById(userSub)) {
            return;
        }
        closedRepository.save(ClosedConversation.builder()
                .userSub(userSub)
                .closedBySub(adminSub)
                .build());
    }

    @Transactional
    public void deleteConversation(String userSub) {
        repository.deleteConversation(userSub);
        closedRepository.deleteById(userSub);
    }

    @Transactional
    public void reopenConversation(String userSub) {
        if (closedRepository.existsById(userSub)) {
            closedRepository.deleteById(userSub);
        }
    }

    private void rejectIfClosed(String userSub) {
        if (closedRepository.existsById(userSub)) {
            throw new BadRequestException("Bu sohbet kapatıldı, yeni mesaj gönderemezsin");
        }
    }
}
