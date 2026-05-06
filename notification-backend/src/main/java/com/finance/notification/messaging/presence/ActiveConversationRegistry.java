package com.finance.notification.messaging.presence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Tracks which conversation key each authenticated principal is currently viewing.
 *
 * <p>Key conventions:
 * <ul>
 *   <li>{@code admin} — a regular user looking at the (single) admin thread.</li>
 *   <li>{@code user:{userSub}} — an admin looking at a specific user's thread.</li>
 * </ul>
 *
 * <p>Used by {@link com.finance.notification.messaging.service.MessageService} to suppress
 * cross-channel notifications and email when the recipient is already viewing the thread,
 * and to mark such messages read on the spot.
 */
@Log4j2
@Component
public class ActiveConversationRegistry {

    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    private static final long MAX_ENTRIES = 50_000L;

    private final Cache<String, String> activeKeys = Caffeine.newBuilder()
            .expireAfterAccess(PRESENCE_TTL)
            .maximumSize(MAX_ENTRIES)
            .build();

    public void register(String userSub, String key) {
        activeKeys.put(userSub, key);
        log.debug("Presence register userSub={} key={}", userSub, key);
    }

    public void unregister(String userSub) {
        activeKeys.invalidate(userSub);
        log.debug("Presence unregister userSub={}", userSub);
    }

    public Optional<String> getActiveKey(String userSub) {
        return Optional.ofNullable(activeKeys.getIfPresent(userSub));
    }

    public boolean isAnyoneActiveOn(String key) {
        return activeKeys.asMap().containsValue(key);
    }
}
