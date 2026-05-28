package com.finance.notification.core.controller;

import com.finance.common.security.UserStatusPort;
import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint that streams live notifications to the authenticated user. Inactive
 * users are rejected with 403; otherwise an emitter is registered for real-time push.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final NotificationStreamRegistry registry;
    private final UserStatusPort userStatus;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        String userSub = jwt.getSubject();
        if (!userStatus.isActive(userSub)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return registry.register(userSub);
    }
}
