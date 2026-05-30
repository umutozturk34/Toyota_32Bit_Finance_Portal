package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

/** Payload for system/broadcast notifications, with an optional issuer for audit. */
public record SystemPayload(
        String title,
        String body,
        String issuedBy
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.SYSTEM;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", title);
        metadata.put("body", body);
        if (issuedBy != null) metadata.put("issuedBy", issuedBy);
        return metadata;
    }
}
