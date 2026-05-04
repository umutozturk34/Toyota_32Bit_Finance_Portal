package com.finance.notification.core.dto;

import java.time.LocalTime;

public record NotificationPreferenceResponse(
        boolean emailEnabled,
        boolean emailPriceAlerts,
        boolean inappPriceAlerts,
        boolean emailWatchlist,
        boolean inappWatchlist,
        boolean emailReports,
        boolean inappReports,
        boolean emailMessages,
        boolean inappMessages,
        boolean emailSystem,
        boolean inappSystem,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd
) {
}
