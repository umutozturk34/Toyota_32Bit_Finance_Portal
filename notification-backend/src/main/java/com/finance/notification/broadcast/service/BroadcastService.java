package com.finance.notification.broadcast.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.broadcast.dto.BroadcastResult;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final RecipientDirectory recipientDirectory;
    private final NotificationDispatcher dispatcher;
    private final BroadcastProperties properties;

    public BroadcastResult broadcast(String adminSub, BroadcastRequest request) {
        long total = recipientDirectory.count();
        if (total > properties.maxRecipients()) {
            throw new BadRequestException(
                    "Broadcast recipients (" + total + ") exceeds limit (" + properties.maxRecipients() + ")");
        }
        SystemPayload payload = new SystemPayload(request.title(), request.body(), adminSub);
        long dispatched = 0;
        long failed = 0;
        int pageIndex = 0;
        Page<String> page;
        do {
            page = recipientDirectory.findUserSubs(PageRequest.of(pageIndex, properties.batchSize()));
            for (String userSub : page.getContent()) {
                if (dispatchSafely(userSub, payload)) {
                    dispatched++;
                } else {
                    failed++;
                }
            }
            pageIndex++;
        } while (page.hasNext());
        log.info("Broadcast complete admin={} title={} total={} dispatched={} failed={}",
                adminSub, request.title(), total, dispatched, failed);
        return new BroadcastResult(total, dispatched, failed);
    }

    private boolean dispatchSafely(String userSub, SystemPayload payload) {
        try {
            dispatcher.dispatch(NotificationRequest.of(userSub, payload));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Broadcast dispatch failed userSub={} reason={}", userSub, ex.getMessage());
            return false;
        }
    }
}
