package com.finance.notification.core.dto;

import java.time.LocalTime;

public record NotificationPreferenceUpdateRequest(
        Boolean emailPriceAlerts,
        Boolean inappPriceAlerts,
        Boolean emailWatchlist,
        Boolean inappWatchlist,
        Boolean emailReports,
        Boolean inappReports,
        Boolean emailMessages,
        Boolean inappMessages,
        Boolean emailSystem,
        Boolean inappSystem,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {
}
