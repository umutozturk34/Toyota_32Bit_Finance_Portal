package com.finance.notification.news;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.NewsPublishedEvent;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.NewsPublishedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class NewsPublishedListener {

    private static final String GROUP_ID = "notification-news-published";

    private final NotificationDispatcher dispatcher;
    private final NotificationPreferenceRepository preferences;
    private final Cache<String, Boolean> processedEventIds;

    public NewsPublishedListener(NotificationDispatcher dispatcher,
                                 NotificationPreferenceRepository preferences,
                                 @Qualifier("newsPublishedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.dispatcher = dispatcher;
        this.preferences = preferences;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = KafkaTopics.NEWS_PUBLISHED,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onNewsPublished(NewsPublishedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate news.published event {}, skip", event.eventId());
            ack.acknowledge();
            return;
        }
        NewsPublishedPayload payload = new NewsPublishedPayload(
                event.articleCount(),
                event.categories(),
                event.sampleTitles(),
                event.source());
        for (NotificationPreference pref : preferences.findAll()) {
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
