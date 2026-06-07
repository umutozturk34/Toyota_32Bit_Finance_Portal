package com.finance.notification.core.controller;

import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStreamControllerTest {

    private static final String SUB = "user-1";

    @Mock private NotificationStreamRegistry registry;
    @Mock private UserStatusPort userStatus;

    private NotificationStreamController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new NotificationStreamController(registry, userStatus);
        jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUB)
                .claim("sub", SUB)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void should_registerEmitter_when_userIsActive() {
        SseEmitter emitter = new SseEmitter();
        when(userStatus.isActive(SUB)).thenReturn(true);
        when(registry.register(SUB)).thenReturn(emitter);

        SseEmitter result = controller.stream(jwt);

        assertThat(result).isSameAs(emitter);
        verify(registry).register(SUB);
    }

    @Test
    void should_rejectWithForbidden_when_userIsInactive() {
        when(userStatus.isActive(SUB)).thenReturn(false);

        assertThatThrownBy(() -> controller.stream(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(registry);
    }
}
