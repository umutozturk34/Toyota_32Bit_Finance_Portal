package com.finance.notification.core.dto;

public record NotificationPreferenceUpdateRequest(
        Boolean emailEnabled,
        Boolean emailPriceAlerts,
        Boolean inappPriceAlerts,
        Boolean emailWatchlist,
        Boolean inappWatchlist,
        Boolean emailSystem,
        Boolean inappSystem,
        Boolean emailMarketOpened,
        Boolean inappMarketOpened,
        Boolean emailMarketClosed,
        Boolean inappMarketClosed,
        Boolean emailMarketDataUpdated,
        Boolean inappMarketDataUpdated,
        Boolean emailNewsPublished,
        Boolean inappNewsPublished,
        Boolean emailPortfolioUpdated,
        Boolean inappPortfolioUpdated,
        Boolean emailMacroIndicators,
        Boolean inappMacroIndicators,
        String marketSessionMarkets
) {
}
