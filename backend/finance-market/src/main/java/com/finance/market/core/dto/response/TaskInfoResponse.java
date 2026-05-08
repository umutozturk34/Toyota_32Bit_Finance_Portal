package com.finance.market.core.dto.response;

public record TaskInfoResponse(
        String type,
        String status,
        String message,
        String startedAt,
        String completedAt,
        Long durationMs,
        String error) {
}
