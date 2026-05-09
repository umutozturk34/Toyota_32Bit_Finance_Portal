package com.finance.user.dto;

public record UserChartBundleResponse(
        UserChartPreferenceResponse preferences,
        UserChartDrawingResponse drawings
) {
}
