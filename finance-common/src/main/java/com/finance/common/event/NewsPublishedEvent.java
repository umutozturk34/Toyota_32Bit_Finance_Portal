package com.finance.common.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NewsPublishedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        int articleCount,
        List<String> categories,
        List<String> sampleTitles,
        String source
) implements DomainEvent {

    public static NewsPublishedEvent of(int articleCount,
                                        List<String> categories,
                                        List<String> sampleTitles,
                                        String source) {
        return new NewsPublishedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                articleCount,
                categories == null ? List.of() : List.copyOf(categories),
                sampleTitles == null ? List.of() : List.copyOf(sampleTitles),
                source
        );
    }

    @Override
    public String topic() {
        return KafkaTopics.NEWS_PUBLISHED;
    }

    @Override
    public String partitionKey() {
        return eventId;
    }
}
