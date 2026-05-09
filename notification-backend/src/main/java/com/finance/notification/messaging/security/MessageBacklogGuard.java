package com.finance.notification.messaging.security;

import com.finance.notification.config.MessagingProperties;
import com.finance.notification.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Log4j2
@Component
@RequiredArgsConstructor
public class MessageBacklogGuard {

    private static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final MessageRepository repository;
    private final MessagingProperties properties;

    public boolean wouldExceedBacklog(String senderSub) {
        LocalDateTime since = repository.findLastAdminReplyAt(senderSub).orElse(EPOCH);
        long unanswered = repository.countUserToAdminSince(senderSub, since);
        if (unanswered >= properties.backlogMaxUnanswered()) {
            log.debug("Backlog limit reached senderSub={} unanswered={}", senderSub, unanswered);
            return true;
        }
        return false;
    }

    public int maxUnanswered() {
        return properties.backlogMaxUnanswered();
    }
}
