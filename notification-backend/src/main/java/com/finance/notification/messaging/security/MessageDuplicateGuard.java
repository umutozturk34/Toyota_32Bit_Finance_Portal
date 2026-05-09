package com.finance.notification.messaging.security;

import com.finance.notification.config.MessagingProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Log4j2
@Component
public class MessageDuplicateGuard {

    private final Cache<String, String> recentBodyHashByUser;

    public MessageDuplicateGuard(MessagingProperties properties) {
        this.recentBodyHashByUser = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.duplicateWindowSeconds()))
                .maximumSize(50_000)
                .build();
    }

    public boolean isDuplicate(String userSub, String body) {
        String hash = sha256(body);
        String existing = recentBodyHashByUser.getIfPresent(userSub);
        if (hash.equals(existing)) {
            log.debug("Skipping duplicate message senderSub={} hash={}", userSub, hash);
            return true;
        }
        recentBodyHashByUser.put(userSub, hash);
        return false;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
