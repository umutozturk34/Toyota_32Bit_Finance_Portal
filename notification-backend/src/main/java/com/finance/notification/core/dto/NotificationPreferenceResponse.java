package com.finance.notification.core.dto;

public record NotificationPreferenceResponse(
        boolean emailEnabled,
        boolean emailPriceAlerts,
        boolean inappPriceAlerts,
        boolean emailWatchlist,
        boolean inappWatchlist,
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
        boolean emailMacroIndicators,
        boolean inappMacroIndicators,
        String marketSessionMarkets
) {
}
