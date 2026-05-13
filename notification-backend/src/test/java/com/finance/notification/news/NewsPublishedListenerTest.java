package com.finance.notification.news;

import com.finance.common.event.NewsPublishedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsPublishedListenerTest {

    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private NewsReader newsReader;
    @SuppressWarnings("unchecked")
    @Mock private Cache<String, Boolean> processedEventIds;
    @Mock private Acknowledgment ack;

    private NewsPublishedListener listener;

    @BeforeEach
    void setUp() {
        listener = new NewsPublishedListener(fanoutService, preferences, newsReader, processedEventIds);
    }

    private NewsPublishedEvent event() {
        return new NewsPublishedEvent("evt-1", OffsetDateTime.now(), "scheduler");
    }

    @Test
    void onNewsPublished_skips_whenEventAlreadyProcessed() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(Boolean.TRUE);

        listener.onNewsPublished(event(), ack);

        verify(fanoutService, never()).fanout(anyString(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onNewsPublished_skipsFanout_whenNoRecentArticles() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(newsReader.findRecent())
                .thenReturn(new NewsReader.RecentNews(0, List.of(), List.of()));

        listener.onNewsPublished(event(), ack);

        verify(fanoutService, never()).fanout(anyString(), any(), any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }

    @Test
    void onNewsPublished_invokesFanout_whenArticlesPresent() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(newsReader.findRecent())
                .thenReturn(new NewsReader.RecentNews(5, List.of("STOCK"), List.of("BIST yükseldi")));
        when(fanoutService.fanout(anyString(), any(), any()))
                .thenReturn(new NotificationFanoutService.FanoutResult(10, 0));

        listener.onNewsPublished(event(), ack);

        verify(fanoutService).fanout(anyString(), any(), any());
        verify(processedEventIds).put("evt-1", Boolean.TRUE);
        verify(ack).acknowledge();
    }
}
