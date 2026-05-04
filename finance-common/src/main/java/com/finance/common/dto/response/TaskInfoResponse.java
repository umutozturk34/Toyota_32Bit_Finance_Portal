package com.finance.common.dto.response;

public record TaskInfoResponse(
        String type,
        String status,
        String message,
        String startedAt,
        String completedAt,
        Long durationMs,
        String error) {
}
