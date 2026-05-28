package com.finance.notification.broadcast.service;

import com.finance.common.exception.BadRequestException;
import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.broadcast.dto.BroadcastResult;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationDispatcher.BatchResult;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a system notification to the whole user base. Rejects when the recipient count exceeds the
 * configured cap, then pages through recipients dispatching in batches and skips the issuing admin
 * so they don't notify themselves. Honours each recipient's notification preferences via the dispatcher.
 */
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
                    "error.broadcast.recipientLimit", total, properties.maxRecipients());
        }
        SystemPayload payload = new SystemPayload(request.title(), request.body(), adminSub);
        long dispatched = 0;
        long failed = 0;
        int pageIndex = 0;
        Page<String> page;
        do {
            page = recipientDirectory.findUserSubs(PageRequest.of(pageIndex, properties.batchSize()));
            List<NotificationRequest> requests = new ArrayList<>(page.getNumberOfElements());
            for (String userSub : page.getContent()) {
                if (userSub.equals(adminSub)) continue;
                requests.add(NotificationRequest.of(userSub, payload));
            }
            if (!requests.isEmpty()) {
                BatchResult result = dispatcher.dispatchBatched(requests);
                dispatched += result.dispatched();
                failed += result.failed();
            }
            pageIndex++;
        } while (page.hasNext());
        log.info("Broadcast complete admin={} title={} total={} dispatched={} failed={}",
                adminSub, request.title(), total, dispatched, failed);
        return new BroadcastResult(total, dispatched, failed);
    }
}
