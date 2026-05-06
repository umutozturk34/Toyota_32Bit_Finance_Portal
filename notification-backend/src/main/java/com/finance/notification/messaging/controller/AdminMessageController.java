package com.finance.notification.messaging.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.notification.messaging.dto.AdminMessageSendRequest;
import com.finance.notification.messaging.dto.ConversationSummary;
import com.finance.notification.messaging.dto.ConversationThread;
import com.finance.notification.messaging.dto.MessageResponse;
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
@RequestMapping("/api/v1/admin/messages")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Validated
public class AdminMessageController {

    private final MessageService service;

    @PostMapping
    public ApiResponse<MessageResponse> sendToUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminMessageSendRequest request) {
        return ApiResponse.success("Message sent to user",
                service.sendAdminToUser(jwt.getSubject(), request.recipientSub(), request.body()));
    }

    @GetMapping("/inbox")
    public ApiResponse<PagedResponse<MessageResponse>> inbox(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<MessageResponse> result = service.getAdminInbox(page, size);
        return ApiResponse.success("Admin inbox retrieved",
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/inbox-count")
    public ApiResponse<Long> inboxCount() {
        return ApiResponse.success("Admin inbox count retrieved", service.getAdminInboxCount());
    }

    @GetMapping("/conversations")
    public ApiResponse<PagedResponse<ConversationSummary>> listConversations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<ConversationSummary> result = service.listConversations(page, size);
        return ApiResponse.success("Conversations retrieved",
                PagedResponse.of(result.getContent(), page, size, result.getTotalElements()));
    }

    @GetMapping("/conversations/{userSub}")
    public ApiResponse<ConversationThread> getConversation(@PathVariable String userSub) {
        return ApiResponse.success("Conversation thread retrieved", service.getConversation(userSub));
    }

    @PostMapping("/conversations/{userSub}/mark-read")
    public ApiResponse<Integer> markRead(@PathVariable String userSub) {
        int affected = service.markAdminInboxRead(userSub);
        return ApiResponse.success("Conversation marked as read", affected);
    }

    @PostMapping("/conversations/{userSub}/close")
    public ApiResponse<Void> closeConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userSub) {
        service.closeConversation(userSub, jwt.getSubject());
        return ApiResponse.success("Conversation closed", null);
    }

    @PostMapping("/conversations/{userSub}/reopen")
    public ApiResponse<Void> reopenConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userSub) {
        service.reopenConversation(userSub, jwt.getSubject());
        return ApiResponse.success("Conversation reopened", null);
    }

    @DeleteMapping("/conversations/{userSub}")
    public ApiResponse<Void> deleteConversation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userSub) {
        service.deleteConversation(userSub, jwt.getSubject());
        return ApiResponse.success("Conversation deleted", null);
    }
}
