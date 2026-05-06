package com.finance.notification.core.model;

import java.util.function.Predicate;

public enum NotificationType {
    PRICE_ALERT_FIRED(NotificationPreference::isEmailPriceAlerts, NotificationPreference::isInappPriceAlerts),
    WATCHLIST_DELTA(NotificationPreference::isEmailWatchlist, NotificationPreference::isInappWatchlist),
    REPORT_READY(NotificationPreference::isEmailReports, NotificationPreference::isInappReports),
    MESSAGE(NotificationPreference::isEmailMessages, NotificationPreference::isInappMessages),
    SYSTEM(NotificationPreference::isEmailSystem, NotificationPreference::isInappSystem),
    MARKET_OPENED(NotificationPreference::isEmailMarketOpened, NotificationPreference::isInappMarketOpened),
    MARKET_DATA_UPDATED(NotificationPreference::isEmailMarketDataUpdated, NotificationPreference::isInappMarketDataUpdated);

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
