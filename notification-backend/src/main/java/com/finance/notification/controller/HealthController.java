package com.finance.notification.controller;

import com.finance.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness probe for the notification service, exposing a public {@code /ping} endpoint. */
@RestController
@RequestMapping("/api/v1/notifications")
public class HealthController {

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("Notification service is up", "pong");
    }
}
