package com.finance.user.dto;

import com.finance.user.dto.enums.ThemePreference;

/** Current user's resolved preferences, including the onboarding-completed flag. */
public record UserPreferenceResponse(
        String userSub,
        ThemePreference theme,
        String language,
        String timezone,
        String defaultChartRange,
        Boolean onboardingCompleted
) {
}
