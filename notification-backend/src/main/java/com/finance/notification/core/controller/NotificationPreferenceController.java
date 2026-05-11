package com.finance.notification.core.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.core.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-preferences")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<NotificationPreferenceResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.notificationPreferences.retrieved"),
                service.getOrDefault(jwt.getSubject()));
    }

    @PatchMapping
    public ApiResponse<NotificationPreferenceResponse> upsert(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.notificationPreferences.updated"),
                service.upsert(jwt.getSubject(), request));
    }
}
