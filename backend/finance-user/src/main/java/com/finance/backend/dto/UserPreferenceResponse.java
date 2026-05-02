package com.finance.backend.dto;

import com.finance.backend.dto.enums.ReportFrequency;
import com.finance.backend.dto.enums.ThemePreference;

public record UserPreferenceResponse(
        String userSub,
        ThemePreference theme,
        String language,
        String timezone,
        String defaultChartRange,
        ReportFrequency reportFrequency,
        Boolean onboardingCompleted
) {
}
