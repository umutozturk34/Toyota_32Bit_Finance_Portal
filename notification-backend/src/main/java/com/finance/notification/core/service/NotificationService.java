package com.finance.notification.core.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String userSub, int page, int size, boolean unreadOnly) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? repository.findByUserSubAndReadAtIsNullOrderByCreatedAtDesc(userSub, pageable)
                : repository.findByUserSubOrderByCreatedAtDesc(userSub, pageable);
        return result.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userSub) {
        return repository.countByUserSubAndReadAtIsNull(userSub);
    }

    @Transactional
    public NotificationResponse markRead(Long id, String userSub) {
        Notification notification = ownedOr404(id, userSub);
        notification.markRead();
        return mapper.toResponse(repository.save(notification));
    }

    @Transactional
    public int markAllRead(String userSub) {
        return repository.markAllRead(userSub, LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id, String userSub) {
        Notification notification = ownedOr404(id, userSub);
        repository.delete(notification);
    }

    private Notification ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(n -> n.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found id=" + id));
    }
}
