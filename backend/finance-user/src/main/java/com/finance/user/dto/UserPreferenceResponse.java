package com.finance.user.dto;

import com.finance.user.dto.enums.ThemePreference;

public record UserPreferenceResponse(
        String userSub,
        ThemePreference theme,
        String language,
        String timezone,
        String defaultChartRange,
        Boolean onboardingCompleted
) {
}
