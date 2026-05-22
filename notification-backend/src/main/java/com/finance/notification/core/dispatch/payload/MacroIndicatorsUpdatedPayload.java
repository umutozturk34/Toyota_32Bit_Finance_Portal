package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MacroIndicatorsUpdatedPayload(
        List<IndicatorChange> changes,
        String source
) implements NotificationPayload {

    @Override
    public NotificationType type() {
        return NotificationType.MACRO_INDICATORS_UPDATED;
    }

    @Override
    public Map<String, Object> toMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("changedCount", changes == null ? 0 : changes.size());
        if (source != null) metadata.put("source", source);
        return metadata;
    }
}
