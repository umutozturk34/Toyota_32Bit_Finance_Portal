package com.finance.notification.messaging.presence;

import com.finance.notification.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Coordinates presence registration with the corresponding bulk mark-read.
 *
 * <p>When a principal declares they are viewing a thread, every previously unread
 * message in that thread is stamped {@code readAt = now()} in a single JPQL update,
 * mirroring what the dispatcher already does for messages that arrive while the
 * thread is open. Subsequent heartbeats are idempotent: the WHERE clause filters
 * on {@code readAt IS NULL}, so re-registers cost zero rows once the thread is clean.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MessagePresenceService {

    private static final String USER_THREAD_KEY = "admin";
    private static final String ADMIN_THREAD_KEY_PREFIX = "user:";

    private final ActiveConversationRegistry registry;
    private final MessageRepository messages;

    @Transactional
    public void register(String userSub, String key) {
        registry.register(userSub, key);
        LocalDateTime now = LocalDateTime.now();
        if (USER_THREAD_KEY.equals(key)) {
            int updated = messages.markUserInboxRead(userSub, now);
            log.debug("Presence-driven user inbox mark-read userSub={} updated={}", userSub, updated);
        } else if (key.startsWith(ADMIN_THREAD_KEY_PREFIX)) {
            String threadOwner = key.substring(ADMIN_THREAD_KEY_PREFIX.length());
            int updated = messages.markAdminInboxRead(threadOwner, now);
            log.debug("Presence-driven admin inbox mark-read threadOwner={} updated={}", threadOwner, updated);
        }
    }

    public void unregister(String userSub) {
        registry.unregister(userSub);
    }
}
