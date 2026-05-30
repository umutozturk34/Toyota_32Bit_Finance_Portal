package com.finance.notification.core.model;

import java.util.function.Predicate;

/**
 * The notification categories the service can emit. Each constant binds to the
 * {@link NotificationPreference} flags that decide whether the email and in-app channels are wanted,
 * keeping channel routing data-driven rather than branching per type.
 */
public enum NotificationType {
    PRICE_ALERT_FIRED(NotificationPreference::isEmailPriceAlerts, NotificationPreference::isInappPriceAlerts),
    WATCHLIST_DELTA(NotificationPreference::isEmailWatchlist, NotificationPreference::isInappWatchlist),
    SYSTEM(NotificationPreference::isEmailSystem, NotificationPreference::isInappSystem),
    MARKET_OPENED(NotificationPreference::isEmailMarketOpened, NotificationPreference::isInappMarketOpened),
    MARKET_CLOSED(NotificationPreference::isEmailMarketClosed, NotificationPreference::isInappMarketClosed),
    MARKET_DATA_UPDATED(NotificationPreference::isEmailMarketDataUpdated, NotificationPreference::isInappMarketDataUpdated),
    NEWS_PUBLISHED(NotificationPreference::isEmailNewsPublished, NotificationPreference::isInappNewsPublished),
    PORTFOLIO_UPDATED(NotificationPreference::isEmailPortfolioUpdated, NotificationPreference::isInappPortfolioUpdated),
    MACRO_INDICATORS_UPDATED(NotificationPreference::isEmailMacroIndicators, NotificationPreference::isInappMacroIndicators);

    private final Predicate<NotificationPreference> emailAccessor;
    private final Predicate<NotificationPreference> inappAccessor;

    NotificationType(Predicate<NotificationPreference> emailAccessor,
                     Predicate<NotificationPreference> inappAccessor) {
        this.emailAccessor = emailAccessor;
        this.inappAccessor = inappAccessor;
    }

    public boolean isEmailWantedBy(NotificationPreference preference) {
        return emailAccessor.test(preference);
    }

    public boolean isInAppWantedBy(NotificationPreference preference) {
        return inappAccessor.test(preference);
    }
}
