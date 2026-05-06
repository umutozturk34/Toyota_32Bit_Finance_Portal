package com.finance.notification.broadcast.dto;

public record BroadcastResult(
        long totalRecipients,
        long dispatched,
        long failed
) {
}
