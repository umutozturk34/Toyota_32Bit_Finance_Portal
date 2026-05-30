package com.finance.notification.broadcast.dto;

/** Outcome of a broadcast: total recipients considered versus rows dispatched and failed. */
public record BroadcastResult(
        long totalRecipients,
        long dispatched,
        long failed
) {
}
