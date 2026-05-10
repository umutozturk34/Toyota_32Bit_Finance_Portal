package com.finance.notification.broadcast.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.notification.broadcast.dto.BroadcastRequest;
import com.finance.notification.broadcast.dto.BroadcastResult;
import com.finance.notification.broadcast.service.BroadcastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService service;
    private final Translator translator;

    @PostMapping("/broadcast")
    public ApiResponse<BroadcastResult> broadcast(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BroadcastRequest request) {
        BroadcastResult result = service.broadcast(jwt.getSubject(), request);
        return ApiResponse.success(translator.translate("api.broadcast.dispatched"), result);
    }
}
