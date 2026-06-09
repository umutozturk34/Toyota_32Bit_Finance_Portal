package com.finance.notification.core.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.core.dto.NotificationResponse;
import com.finance.notification.core.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API over the authenticated user's in-app notifications: paged listing (optionally
 * unread-only or text-searched), unread count, marking read (single/all) and deletion.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<PagedResponse<NotificationResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String search) {
        Page<NotificationResponse> result = service.list(jwt.getSubject(), page, size, unreadOnly, search);
        return ApiResponse.success(translator.translate("api.notification.listRetrieved"),
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.notification.unreadCountRetrieved"),
                service.unreadCount(jwt.getSubject()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ApiResponse.success(translator.translate("api.notification.markedRead"),
                service.markRead(id, jwt.getSubject()));
    }

    @PostMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.notification.allMarkedRead"),
                service.markAllRead(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.delete(id, jwt.getSubject());
        return ApiResponse.success(translator.translate("api.notification.deleted"), null);
    }

    @DeleteMapping
    public ApiResponse<Integer> deleteAll(@AuthenticationPrincipal Jwt jwt) {
        int removed = service.deleteAll(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.notification.allDeleted"), removed);
    }
}
