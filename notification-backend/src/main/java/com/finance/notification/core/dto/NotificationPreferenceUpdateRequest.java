package com.finance.notification.core.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        @Size(max = 96, message = "{validation.preference.marketSessionMarkets.maxLen}")
        @Pattern(regexp = "^$|^[A-Z_]+(,[A-Z_]+)*$",
                message = "{validation.preference.marketSessionMarkets.format}")
        String marketSessionMarkets
) {
}
