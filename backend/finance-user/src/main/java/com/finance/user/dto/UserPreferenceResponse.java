package com.finance.user.dto;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.user.dto.enums.ReportFrequency;
import com.finance.user.dto.enums.ThemePreference;

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
