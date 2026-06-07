package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dispatch-time tuning: number/price formatting, watchlist-delta body preview size, fanout page size
 * and news-digest windowing.
 */
@ConfigurationProperties("notification.dispatch")
public record NotificationDispatchProperties(
        Formatting formatting,
        WatchlistDelta watchlistDelta,
        Fanout fanout,
        NewsDigest newsDigest
) {

    public NotificationDispatchProperties {
        if (newsDigest == null) newsDigest = new NewsDigest(null, null);
    }

    /**
     * Number-formatting precision: fraction digits for large vs. small magnitudes and the rounding
     * scale applied to change-percent figures rendered in notification text.
     */
    public record Formatting(
            int fractionDigitsLarge,
            int fractionDigitsSmall,
            int changePercentScale
    ) {}

    /** Watchlist-delta tuning: how many changed items to inline as a preview in the notification body. */
    public record WatchlistDelta(
            int bodyPreviewItems
    ) {}

    /** Fan-out tuning: page size for batching recipients when broadcasting a notification to many users. */
    public record Fanout(
            int pageSize
    ) {}

    /**
     * News-digest windowing: the look-back window for "recent" items and the cap on sample titles in
     * the digest body. The compact constructor substitutes 60 minutes and 3 titles for null values.
     */
    public record NewsDigest(
            Integer recentWindowMinutes,
            Integer sampleTitleLimit
    ) {

        public NewsDigest {
            if (recentWindowMinutes == null) recentWindowMinutes = 60;
            if (sampleTitleLimit == null) sampleTitleLimit = 3;
        }
    }
}
