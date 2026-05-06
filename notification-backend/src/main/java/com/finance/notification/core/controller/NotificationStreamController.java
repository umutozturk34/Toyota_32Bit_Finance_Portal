package com.finance.notification.core.controller;

import com.finance.notification.core.dispatch.NotificationStreamRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationStreamController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final NotificationStreamRegistry registry;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        String userSub = jwt.getSubject();
        return isAdmin(auth) ? registry.registerForAdmin(userSub) : registry.register(userSub);
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ADMIN_AUTHORITY.equalsIgnoreCase(authority.getAuthority())) return true;
        }
        return false;
    }
}
