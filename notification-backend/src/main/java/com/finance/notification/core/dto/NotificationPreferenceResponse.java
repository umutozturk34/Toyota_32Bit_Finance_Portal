package com.finance.notification.core.dto;

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
        boolean emailMarketOpened,
        boolean inappMarketOpened,
        boolean emailMarketClosed,
        boolean inappMarketClosed,
        boolean emailMarketDataUpdated,
        boolean inappMarketDataUpdated,
        boolean emailNewsPublished,
        boolean inappNewsPublished,
        boolean emailPortfolioUpdated,
        boolean inappPortfolioUpdated,
        String marketSessionMarkets
) {
}
