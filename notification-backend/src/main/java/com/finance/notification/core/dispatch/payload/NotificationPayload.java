package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.Map;

/**
 * Sealed family of typed notification payloads. Each variant declares its {@link NotificationType}
 * and contributes the metadata persisted on the notification and exposed to clients. The handler for
 * a payload's type is what turns it into rendered content.
 */
public sealed interface NotificationPayload
        permits PriceAlertPayload, WatchlistDeltaPayload, SystemPayload,
                MarketOpenedPayload, MarketClosedPayload, MarketDataUpdatedPayload,
                NewsPublishedPayload, PortfolioUpdatedPayload,
                MacroIndicatorsUpdatedPayload {

    NotificationType type();

    /** Structured metadata stored on the notification; null/empty fields are omitted by convention. */
    Map<String, Object> toMetadata();
}
