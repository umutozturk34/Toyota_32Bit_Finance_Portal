package com.finance.notification.news;

import com.finance.common.event.NewsPublishedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.NewsPublishedPayload;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Log4j2
@Component
public class NewsPublishedListener {

    private static final String GROUP_ID = "notification-news-published";

    private final NotificationFanoutService fanoutService;
    private final NotificationPreferenceRepository preferences;
    private final NewsReader newsReader;
    private final Cache<String, Boolean> processedEventIds;

    public NewsPublishedListener(NotificationFanoutService fanoutService,
                                 NotificationPreferenceRepository preferences,
                                 NewsReader newsReader,
                                 @Qualifier("newsPublishedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.fanoutService = fanoutService;
        this.preferences = preferences;
        this.newsReader = newsReader;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.news-published}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onNewsPublished(NewsPublishedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate news.published event {}, skip", event.eventId());
            ack.acknowledge();
            return;
        }
        NewsReader.RecentNews recent = newsReader.findRecent();
        if (recent.totalCount() == 0) {
            log.info("news.published source={} no recent articles, skip fanout", event.source());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            ack.acknowledge();
            return;
        }
        NewsPublishedPayload payload = new NewsPublishedPayload(
                recent.totalCount(), recent.categories(), recent.sampleTitles(), event.source());
        FanoutResult result = fanoutService.fanout(
                "news.published",
                preferences::findNewsSubscribed,
                pref -> Optional.of(payload));
        log.info("news.published source={} dispatched={} failed={}",
                event.source(), result.dispatched(), result.failed());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
