package com.finance.user.dto;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.user.dto.enums.ReportFrequency;
import com.finance.user.dto.enums.ThemePreference;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserPreferenceUpdateRequest(
        ThemePreference theme,
        @Pattern(regexp = "tr|en", message = "language must be 'tr' or 'en'")
        String language,
        @Size(max = 32, message = "timezone max 32 chars")
        String timezone,
        @Pattern(regexp = "1D|1W|1M|3M|6M|1Y|5Y|ALL", message = "defaultChartRange must be one of 1D|1W|1M|3M|6M|1Y|5Y|ALL")
        String defaultChartRange,
        ReportFrequency reportFrequency,
        Boolean onboardingCompleted
) {
}
