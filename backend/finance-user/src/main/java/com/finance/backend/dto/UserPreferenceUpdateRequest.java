package com.finance.backend.dto;

import com.finance.backend.dto.enums.ReportFrequency;
import com.finance.backend.dto.enums.ThemePreference;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserPreferenceUpdateRequest(
        ThemePreference theme,
        @Pattern(regexp = "tr|en", message = "language must be 'tr' or 'en'")
        String language,
        @Size(max = 32, message = "timezone max 32 chars")
        String timezone,
        @Pattern(regexp = "1D|1W|1M|3M|6M|1Y|ALL", message = "defaultChartRange must be one of 1D|1W|1M|3M|6M|1Y|ALL")
        String defaultChartRange,
        ReportFrequency reportFrequency,
        Boolean onboardingCompleted
) {
}
