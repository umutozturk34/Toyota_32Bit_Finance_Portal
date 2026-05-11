package com.finance.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notification.dispatch")
public record NotificationDispatchProperties(
        Formatting formatting,
        WatchlistDelta watchlistDelta,
        Message message,
        Fanout fanout
) {

    public record Formatting(
            int fractionDigitsLarge,
            int fractionDigitsSmall,
            int changePercentScale
    ) {}

    public record WatchlistDelta(
            int bodyPreviewItems
    ) {}

    public record Message(
            int previewMaxChars
    ) {}

    public record Fanout(
            int pageSize
    ) {}
}
