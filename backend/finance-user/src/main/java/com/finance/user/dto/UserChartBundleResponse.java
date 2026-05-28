package com.finance.user.dto;

/** Combined chart state for one tracked asset: the user's saved config plus drawings, fetched in a single call. */
public record UserChartBundleResponse(
        UserChartPreferenceResponse preferences,
        UserChartDrawingResponse drawings
) {
}
