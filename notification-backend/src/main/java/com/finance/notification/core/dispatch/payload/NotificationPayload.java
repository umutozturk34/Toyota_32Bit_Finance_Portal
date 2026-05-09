package com.finance.notification.core.dispatch.payload;

import com.finance.notification.core.model.NotificationType;

import java.util.Map;

public sealed interface NotificationPayload
        permits PriceAlertPayload, WatchlistDeltaPayload, MessagePayload, SystemPayload,
                MarketOpenedPayload, MarketClosedPayload, MarketDataUpdatedPayload,
                NewsPublishedPayload, PortfolioUpdatedPayload {

    NotificationType type();

    Map<String, Object> toMetadata();
}
