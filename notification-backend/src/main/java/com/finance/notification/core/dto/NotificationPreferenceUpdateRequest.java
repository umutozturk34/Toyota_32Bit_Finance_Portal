package com.finance.notification.core.dto;

/** Partial preference update; boxed booleans are nullable so only supplied flags are changed. */
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
