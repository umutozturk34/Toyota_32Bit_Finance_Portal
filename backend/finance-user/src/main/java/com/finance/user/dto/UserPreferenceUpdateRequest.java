package com.finance.user.dto;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.event.*;
import com.finance.common.repository.*;

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
