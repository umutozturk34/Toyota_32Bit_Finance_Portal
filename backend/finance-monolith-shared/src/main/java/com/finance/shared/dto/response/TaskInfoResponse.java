package com.finance.shared.dto.response;

/** API view of a single tracked task with timestamps as strings and a computed elapsed duration. */
public record TaskInfoResponse(
        String type,
        String status,
        String message,
        String startedAt,
        String completedAt,
        Long durationMs,
        String error) {
}
