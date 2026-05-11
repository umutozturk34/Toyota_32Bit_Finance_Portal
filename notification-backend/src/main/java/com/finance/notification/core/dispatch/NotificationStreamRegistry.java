package com.finance.notification.core.dispatch;

import com.finance.notification.config.NotificationStreamProperties;
import com.finance.notification.core.dto.NotificationResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
@Component
@RequiredArgsConstructor
public class NotificationStreamRegistry {

    private final NotificationStreamProperties streamProperties;

    private final Cache<String, CopyOnWriteArrayList<SseEmitter>> emitters = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .removalListener((String userSub, CopyOnWriteArrayList<SseEmitter> list, RemovalCause cause) -> {
                if (cause.wasEvicted() && list != null) {
                    log.debug("Notification emitters evicted user={} count={} cause={}",
                            userSub, list.size(), cause);
                    list.forEach(SseEmitter::complete);
                }
            })
            .build();

    public SseEmitter register(String userSub) {
        SseEmitter emitter = new SseEmitter(streamProperties.emitterTimeoutMs());
        CopyOnWriteArrayList<SseEmitter> list = emitters.asMap()
                .computeIfAbsent(userSub, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> drop(userSub, emitter));
        emitter.onTimeout(() -> drop(userSub, emitter));
        emitter.onError(t -> drop(userSub, emitter));
        sendInit(emitter);
        return emitter;
    }

    public void publish(String userSub, NotificationResponse event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.getIfPresent(userSub);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(event));
            } catch (IOException | IllegalStateException ex) {
                log.debug("SSE send failed user={}: {}", userSub, ex.getMessage());
                emitter.completeWithError(ex);
            }
        }
    }

    public void publishToUser(String userSub, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.getIfPresent(userSub);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                log.debug("SSE send failed user={} event={}: {}", userSub, eventName, ex.getMessage());
                emitter.completeWithError(ex);
            }
        }
    }

    private void sendInit(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void drop(String userSub, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.getIfPresent(userSub);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.asMap().remove(userSub, list);
        }
    }
}
