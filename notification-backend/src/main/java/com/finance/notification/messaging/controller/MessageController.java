package com.finance.notification.messaging.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.notification.messaging.dto.MessageResponse;
import com.finance.notification.messaging.dto.MessageSendRequest;
import com.finance.notification.messaging.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final MessageService service;

    @PostMapping
    public ApiResponse<MessageResponse> sendToAdmins(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MessageSendRequest request) {
        return ApiResponse.success("Message sent to admins",
                service.sendUserToAdmin(jwt.getSubject(), request.body()));
    }

    @GetMapping("/inbox")
    public ApiResponse<PagedResponse<MessageResponse>> inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<MessageResponse> result = service.getUserInbox(jwt.getSubject(), page, size);
        return ApiResponse.success("Inbox retrieved",
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/sent")
    public ApiResponse<PagedResponse<MessageResponse>> sent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<MessageResponse> result = service.getUserSent(jwt.getSubject(), page, size);
        return ApiResponse.success("Sent retrieved",
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Unread count retrieved",
                service.getUserUnreadCount(jwt.getSubject()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        service.markRead(id, jwt.getSubject());
        return ApiResponse.success("Message marked as read", null);
    }
}
