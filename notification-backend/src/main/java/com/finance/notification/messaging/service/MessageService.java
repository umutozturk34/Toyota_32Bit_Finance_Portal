package com.finance.notification.messaging.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.dispatch.email.KeycloakUserEmailLookup;
import com.finance.notification.core.dispatch.email.KeycloakUserProfile;
import com.finance.notification.messaging.dispatch.AdminInboxEvent;
import com.finance.notification.messaging.dispatch.ConversationLifecycleEvent;
import com.finance.notification.messaging.dispatch.MessageDispatchEvent;
import com.finance.notification.messaging.dto.ConversationSummary;
import com.finance.notification.messaging.dto.ConversationThread;
import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.model.ClosedConversation;
import com.finance.notification.messaging.model.Message;
import com.finance.notification.messaging.model.MessageDirection;
import com.finance.notification.messaging.presence.ActiveConversationRegistry;
import com.finance.notification.messaging.repository.ClosedConversationRepository;
import com.finance.notification.messaging.repository.MessageRepository;
import com.finance.notification.messaging.security.MessageBacklogGuard;
import com.finance.notification.messaging.security.MessageCooldownGuard;
import com.finance.notification.messaging.security.MessageDuplicateGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String ADMIN_THREAD_KEY = "admin";
    private static final String USER_THREAD_KEY_PREFIX = "user:";

    private final MessageRepository repository;
    private final ClosedConversationRepository closedRepository;
    private final MessageMapper mapper;
    private final MessageDuplicateGuard duplicateGuard;
    private final MessageCooldownGuard cooldownGuard;
    private final MessageBacklogGuard backlogGuard;
    private final ApplicationEventPublisher events;
    private final KeycloakUserEmailLookup userDirectory;
    private final ActiveConversationRegistry presence;
    private final UserStatusPort userStatus;

    @Transactional
    public MessageResponse sendUserToAdmin(String senderSub, String body) {
        rejectIfClosed(senderSub);
        rejectIfBacklogFull(senderSub);
        rejectIfCoolingDown(senderSub);
        rejectDuplicate(senderSub, body);
        boolean adminViewing = presence.isAnyoneActiveOn(USER_THREAD_KEY_PREFIX + senderSub);
        Message saved = repository.save(Message.builder()
                .senderSub(senderSub)
                .recipientSub(null)
                .body(body)
                .direction(MessageDirection.USER_TO_ADMIN)
                .readAt(adminViewing ? LocalDateTime.now() : null)
                .build());
        MessageResponse response = mapper.toResponse(saved);
        events.publishEvent(new AdminInboxEvent(response));
        log.info("User-to-admin message sent senderSub={} messageId={} adminViewing={}",
                senderSub, saved.getId(), adminViewing);
        return response;
    }

    @Transactional
    public MessageResponse sendAdminToUser(String adminSub, String recipientSub, String body) {
        if (recipientSub == null || recipientSub.isBlank()) {
            throw new BusinessException("error.message.recipientSubBlank");
        }
        if (adminSub.equals(recipientSub)) {
            throw new BusinessException("error.message.cannotSelfDm");
        }
        if (!userStatus.isActive(recipientSub)) {
            throw new BusinessException("error.message.recipientDisabled");
        }
        rejectIfCoolingDown(adminSub);
        rejectDuplicate(adminSub, body);
        boolean userViewing = presence.getActiveKey(recipientSub)
                .map(ADMIN_THREAD_KEY::equals)
                .orElse(false);
        Message saved = repository.save(Message.builder()
                .senderSub(adminSub)
                .recipientSub(recipientSub)
                .body(body)
                .direction(MessageDirection.ADMIN_TO_USER)
                .readAt(userViewing ? LocalDateTime.now() : null)
                .build());
        events.publishEvent(new MessageDispatchEvent(recipientSub, adminSub, body));
        log.info("Admin-to-user message sent adminSub={} recipientSub={} messageId={} userViewing={}",
                adminSub, recipientSub, saved.getId(), userViewing);
        return mapper.toResponse(saved);
    }

    private void rejectDuplicate(String senderSub, String body) {
        if (duplicateGuard.isDuplicate(senderSub, body)) {
            throw new BadRequestException("error.message.duplicate");
        }
    }

    private void rejectIfCoolingDown(String senderSub) {
        if (cooldownGuard.isCoolingDown(senderSub)) {
            throw new BadRequestException("error.message.tooFrequent");
        }
    }

    private void rejectIfBacklogFull(String senderSub) {
        if (backlogGuard.wouldExceedBacklog(senderSub)) {
            throw new BadRequestException("error.message.unanswered", backlogGuard.maxUnanswered());
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
    public boolean isConversationClosed(String userSub) {
        return closedRepository.existsById(userSub);
    }

    @Transactional(readOnly = true)
    public long getAdminInboxCount() {
        return repository.countByDirection(MessageDirection.USER_TO_ADMIN);
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummary> listConversations(int page, int size, String search) {
        String bodyTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        String[] subFilter = bodyTerm != null
                ? userDirectory.search(bodyTerm, 100).stream()
                        .map(KeycloakUserProfile::sub)
                        .toArray(String[]::new)
                : new String[0];
        boolean hasSubFilter = subFilter.length > 0;
        return repository.findConversationSummaries(bodyTerm, subFilter, hasSubFilter, PageRequest.of(page, size))
                .map(p -> {
                    KeycloakUserProfile profile = userDirectory.findProfile(p.getUserSub()).orElse(null);
                    return new ConversationSummary(
                            p.getUserSub(),
                            profile != null ? profile.username() : null,
                            profile != null ? profile.email() : null,
                            p.getLastBody(),
                            p.getLastSentAt(),
                            Boolean.TRUE.equals(p.getClosed()),
                            p.getUnreadCount() != null ? p.getUnreadCount() : 0L);
                });
    }

    @Transactional
    public void markRead(Long messageId, String userSub) {
        Message message = repository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("error.message.notFound", messageId));
        if (!userSub.equals(message.getRecipientSub())) {
            throw new ResourceNotFoundException("error.message.notFound", messageId);
        }
        if (message.getReadAt() == null) {
            message.setReadAt(LocalDateTime.now());
            repository.save(message);
        } else {
            log.debug("markRead no-op messageId={} userSub={}", messageId, userSub);
        }
    }

    @Transactional(readOnly = true)
    public ConversationThread getConversation(String userSub) {
        List<MessageResponse> messages = repository.findConversation(userSub).stream()
                .map(mapper::toResponse)
                .toList();
        ClosedConversation closed = closedRepository.findById(userSub).orElse(null);
        KeycloakUserProfile profile = userDirectory.findProfile(userSub).orElse(null);
        return new ConversationThread(
                userSub,
                profile != null ? profile.username() : null,
                profile != null ? profile.email() : null,
                closed != null,
                closed != null ? closed.getClosedAt() : null,
                messages);
    }

    @Transactional
    public int markAdminInboxRead(String userSub) {
        return repository.markAdminInboxRead(userSub, LocalDateTime.now());
    }

    @Transactional
    public void closeConversation(String userSub, String adminSub) {
        if (closedRepository.existsById(userSub)) {
            log.debug("closeConversation no-op userSub={} alreadyClosed=true", userSub);
            return;
        }
        closedRepository.save(ClosedConversation.builder()
                .userSub(userSub)
                .closedBySub(adminSub)
                .build());
        events.publishEvent(new ConversationLifecycleEvent(
                userSub, adminSub, ConversationLifecycleEvent.Action.CLOSED));
        log.info("Conversation closed userSub={} by admin={}", userSub, adminSub);
    }

    @Transactional
    public void deleteConversation(String userSub, String adminSub) {
        repository.deleteConversation(userSub);
        closedRepository.deleteById(userSub);
        events.publishEvent(new ConversationLifecycleEvent(
                userSub, adminSub, ConversationLifecycleEvent.Action.DELETED));
        log.info("Conversation deleted userSub={} by admin={}", userSub, adminSub);
    }

    @Transactional
    public void reopenConversation(String userSub, String adminSub) {
        if (closedRepository.existsById(userSub)) {
            closedRepository.deleteById(userSub);
            events.publishEvent(new ConversationLifecycleEvent(
                    userSub, adminSub, ConversationLifecycleEvent.Action.REOPENED));
            log.info("Conversation reopened userSub={} by admin={}", userSub, adminSub);
        } else {
            log.debug("reopenConversation no-op userSub={} notClosed=true", userSub);
        }
    }

    private void rejectIfClosed(String userSub) {
        if (closedRepository.existsById(userSub)) {
            throw new BadRequestException("error.message.closed");
        }
    }
}
