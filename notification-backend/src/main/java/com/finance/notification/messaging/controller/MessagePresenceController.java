package com.finance.notification.messaging.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.notification.messaging.dto.MessagePresenceRequest;
import com.finance.notification.messaging.presence.ActiveConversationRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages/active")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class MessagePresenceController {

    private final ActiveConversationRegistry registry;

    @PostMapping
    public ApiResponse<Void> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MessagePresenceRequest request) {
        registry.register(jwt.getSubject(), request.key());
        return ApiResponse.success("Active conversation registered", null);
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(@AuthenticationPrincipal Jwt jwt) {
        registry.unregister(jwt.getSubject());
        return ApiResponse.success("Active conversation cleared", null);
    }
}
