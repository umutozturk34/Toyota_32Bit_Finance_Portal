package com.finance.notification.core.dto;

public record NotificationPreferenceUpdateRequest(
        Boolean emailEnabled,
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
        Boolean emailMarketOpened,
        Boolean inappMarketOpened,
        Boolean emailMarketDataUpdated,
        Boolean inappMarketDataUpdated,
        String marketSessionMarkets
) {
}
