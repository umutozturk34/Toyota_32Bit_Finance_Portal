package com.finance.notification.core.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Read/update operations on a user's notifications: listing (with optional unread filter and
 * text search over title/body), unread count, marking read and deletion. All mutations enforce
 * ownership, returning 404 when a notification does not belong to the caller.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String userSub, int page, int size, boolean unreadOnly, String search) {
        Pageable pageable = PageRequest.of(page, size);
        boolean hasSearch = search != null && !search.isBlank();
        Page<Notification> result;
        if (hasSearch && unreadOnly) {
            result = repository.searchUnreadByUserSub(userSub, search.trim(), pageable);
        } else if (hasSearch) {
            result = repository.searchByUserSub(userSub, search.trim(), pageable);
        } else if (unreadOnly) {
            result = repository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(userSub, pageable);
        } else {
            result = repository.findByUserSubOrderByCreatedAtDesc(userSub, pageable);
        }
        return result.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userSub) {
        return repository.countByUserSubAndReadAtIsNull(userSub);
    }

    @Transactional
    public NotificationResponse markRead(Long id, String userSub) {
        Notification notification = ownedOr404(id, userSub);
        if (!notification.isUnread()) {
            log.debug("Notification markRead no-op id={} userSub={}", id, userSub);
        }
        notification.markRead();
        return mapper.toResponse(repository.save(notification));
    }

    @Transactional
    public int markAllRead(String userSub) {
        int updated = repository.markAllRead(userSub, LocalDateTime.now());
        log.info("Notifications markAllRead userSub={} updated={}", userSub, updated);
        return updated;
    }

    @Transactional
    public void delete(Long id, String userSub) {
        Notification notification = ownedOr404(id, userSub);
        repository.delete(notification);
        log.info("Notification deleted id={} userSub={}", id, userSub);
    }

    @Transactional
    public int deleteAll(String userSub) {
        int removed = repository.deleteAllByUserSub(userSub);
        log.info("Notifications deleteAll userSub={} removed={}", userSub, removed);
        return removed;
    }

    private Notification ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(n -> n.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.notification.notFound", id));
    }
}
