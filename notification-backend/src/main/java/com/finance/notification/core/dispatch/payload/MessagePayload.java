package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.HashMap;
import java.util.Map;

public record MessagePayload(
        String senderSub,
        String body
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.MESSAGE;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        if (senderSub != null) metadata.put("senderSub", senderSub);
        if (body != null) metadata.put("body", body);
        return metadata;
    }
}
