package com.finance.notification.core.dispatch;

import com.finance.notification.config.NotificationStreamProperties;
import com.finance.notification.core.dto.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers the failure paths of the SSE registry that the happy-path tests skip: a send that throws
 * during {@code publish}/{@code publishToUser} is surfaced via {@link SseEmitter#completeWithError},
 * and a connection that fails its init handshake is completed with the error. Emitters are stubbed
 * through construction interception so the registry tracks the mock instances it creates internally;
 * the init handshake send is allowed to succeed so only the targeted call fails.
 */
class NotificationStreamRegistryErrorTest {

    private NotificationStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationStreamRegistry(new NotificationStreamProperties(60_000L, 30, 10_000));
    }

    @Test
    void should_completeWithError_when_publishSendThrowsIoException() {
        AtomicInteger calls = new AtomicInteger();
        try (var mocked = mockConstruction(SseEmitter.class, (emitter, ctx) ->
                org.mockito.Mockito.doAnswer(inv -> {
                    if (calls.getAndIncrement() == 0) return null;
                    throw new IOException("broken pipe");
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            registry.register("user-1");
            SseEmitter created = mocked.constructed().get(0);
            NotificationResponse event = mock(NotificationResponse.class);

            registry.publish("user-1", event);

            verify(created, times(1)).completeWithError(any(IOException.class));
        }
    }

    @Test
    void should_completeWithError_when_publishToUserSendThrowsIllegalState() {
        AtomicInteger calls = new AtomicInteger();
        try (var mocked = mockConstruction(SseEmitter.class, (emitter, ctx) ->
                org.mockito.Mockito.doAnswer(inv -> {
                    if (calls.getAndIncrement() == 0) return null;
                    throw new IllegalStateException("closed");
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            registry.register("user-1");
            SseEmitter created = mocked.constructed().get(0);

            registry.publishToUser("user-1", "unread-count", 3);

            verify(created, times(1)).completeWithError(any(IllegalStateException.class));
        }
    }

    @Test
    void should_completeWithError_when_initHandshakeFails() {
        try (var mocked = mockConstruction(SseEmitter.class, (emitter, ctx) ->
                org.mockito.Mockito.doThrow(new IOException("no init"))
                        .when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            SseEmitter result = registry.register("user-2");

            SseEmitter created = mocked.constructed().get(0);
            assertThat(result).isSameAs(created);
            verify(created).completeWithError(any(IOException.class));
        }
    }

    @Test
    void should_pushSuccessfully_when_emitterAcceptsEvent() {
        try (var mocked = mockConstruction(SseEmitter.class)) {
            registry.register("user-3");
            SseEmitter created = mocked.constructed().get(0);
            NotificationResponse event = mock(NotificationResponse.class);

            registry.publish("user-3", event);

            verify(created, never()).completeWithError(any());
        }
    }
}
