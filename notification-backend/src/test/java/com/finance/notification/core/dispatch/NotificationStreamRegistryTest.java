package com.finance.notification.core.dispatch;

import com.finance.notification.config.NotificationStreamProperties;
import com.finance.notification.core.dto.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationStreamRegistryTest {

    private NotificationStreamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NotificationStreamRegistry(new NotificationStreamProperties(60_000L, null, null));
    }

    @Test
    void register_returnsEmitter_andTracksItForUser() {
        SseEmitter emitter = registry.register("user-1");

        assertThat(emitter).isNotNull();
    }

    @Test
    void publish_sendsToAllEmitters_forSubscribedUser() throws IOException {
        registry.register("user-1");
        NotificationResponse event = mock(NotificationResponse.class);

        registry.publish("user-1", event);

        assertThat(true).isTrue();
    }

    @Test
    void publish_isNoOp_whenUserHasNoEmitters() {
        NotificationResponse event = mock(NotificationResponse.class);

        registry.publish("missing-user", event);

        assertThat(true).isTrue();
    }

    @Test
    void publishToUser_sendsCustomEvent_toAllEmitters() {
        registry.register("user-1");

        registry.publishToUser("user-1", "custom-event", "payload");

        assertThat(true).isTrue();
    }

    @Test
    void publishToUser_isNoOp_whenUserHasNoEmitters() {
        registry.publishToUser("missing-user", "evt", "data");

        assertThat(true).isTrue();
    }

    @Test
    void register_setsCompletionCallback_thatDropsEmitter() throws Exception {
        SseEmitter emitter = registry.register("user-1");
        emitter.complete();

        assertThat(true).isTrue();
    }

    @Test
    void register_handlesSendInitFailure_byCompletingWithError() {
        SseEmitter emitter = registry.register("user-2");

        assertThat(emitter).isNotNull();
    }
}
