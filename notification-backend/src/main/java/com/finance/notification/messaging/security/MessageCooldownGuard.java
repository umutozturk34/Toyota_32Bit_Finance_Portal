package com.finance.notification.messaging.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Log4j2
@Component
public class MessageCooldownGuard {

    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final Cache<String, Instant> lastSentBySender = Caffeine.newBuilder()
            .expireAfterWrite(COOLDOWN.multipliedBy(2))
            .maximumSize(50_000)
            .build();

    public boolean isCoolingDown(String senderSub) {
        Instant now = Instant.now();
        Instant lastSent = lastSentBySender.getIfPresent(senderSub);
        if (lastSent != null && now.isBefore(lastSent.plus(COOLDOWN))) {
            log.debug("Cooldown active senderSub={} wait={}", senderSub, Duration.between(now, lastSent.plus(COOLDOWN)));
            return true;
        }
        lastSentBySender.put(senderSub, now);
        return false;
    }
}
