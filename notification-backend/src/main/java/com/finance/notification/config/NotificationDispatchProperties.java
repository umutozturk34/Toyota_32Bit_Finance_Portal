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

    public record Formatting(
            int fractionDigitsLarge,
            int fractionDigitsSmall,
            int changePercentScale
    ) {}

    public record WatchlistDelta(
            int bodyPreviewItems
    ) {}

    public record Fanout(
            int pageSize
    ) {}

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
