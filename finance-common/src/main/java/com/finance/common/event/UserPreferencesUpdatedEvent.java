package com.finance.common.event;

import java.time.OffsetDateTime;

public record UserPreferencesUpdatedEvent(
        String eventId,
        String userSub,
        OffsetDateTime occurredAt,
        String theme,
        String language,
        String timezone,
        String defaultChartRange,
        String reportFrequency,
        Boolean onboardingCompleted
) {
}
