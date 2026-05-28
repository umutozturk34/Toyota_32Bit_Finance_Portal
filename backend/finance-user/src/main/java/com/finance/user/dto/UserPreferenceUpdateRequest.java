package com.finance.user.dto;

import com.finance.user.dto.enums.ThemePreference;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial preference update: any null field is left unchanged. Validation pins language to {@code tr|en}
 * and chart range to the supported codes; {@code onboardingCompleted} lets the client mark onboarding done.
 */
public record UserPreferenceUpdateRequest(
        ThemePreference theme,
        @Pattern(regexp = "tr|en", message = "{validation.language.pattern}")
        String language,
        @Size(max = 32, message = "{validation.timezone.size}")
        String timezone,
        @Pattern(regexp = "1D|1W|1M|3M|6M|1Y|3Y|5Y|ALL", message = "{validation.defaultChartRange.pattern}")
        String defaultChartRange,
        Boolean onboardingCompleted
) {
}
