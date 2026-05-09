package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record NewsPublishedPayload(
        int articleCount,
        List<String> categories,
        List<String> sampleTitles,
        String source
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.NEWS_PUBLISHED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("articleCount", articleCount);
        if (categories != null && !categories.isEmpty()) metadata.put("categories", categories);
        if (sampleTitles != null && !sampleTitles.isEmpty()) metadata.put("sampleTitles", sampleTitles);
        if (source != null) metadata.put("source", source);
        return metadata;
    }
}
