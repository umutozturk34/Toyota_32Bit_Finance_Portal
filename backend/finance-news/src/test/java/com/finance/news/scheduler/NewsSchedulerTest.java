package com.finance.news.scheduler;

import com.finance.common.event.NewsPublishedEvent;
import com.finance.news.service.article.NewsDataService;
import com.finance.shared.event.EventPublisherPort;
import com.finance.shared.service.TaskTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSchedulerTest {

    private NewsDataService newsDataService;
    private TaskTrackingService taskTracker;
    private ObjectProvider<EventPublisherPort> events;
    private EventPublisherPort publisher;
    private NewsScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        newsDataService = mock(NewsDataService.class);
        taskTracker = mock(TaskTrackingService.class);
        events = mock(ObjectProvider.class);
        publisher = mock(EventPublisherPort.class);
        scheduler = new NewsScheduler(newsDataService, taskTracker, events);
        runTrackedRunnableInline();
    }

    /** Makes the tracker execute the supplied task body synchronously so we can assert its effects. */
    private void runTrackedRunnableInline() {
        doAnswer(invocation -> {
            Runnable body = invocation.getArgument(2);
            body.run();
            return null;
        }).when(taskTracker).runTracked(anyString(), anyString(), any(Runnable.class));
    }

    @SuppressWarnings("unchecked")
    private void publisherAvailable() {
        doAnswer(invocation -> {
            ((Consumer<EventPublisherPort>) invocation.getArgument(0)).accept(publisher);
            return null;
        }).when(events).ifAvailable(any(Consumer.class));
    }

    @Test
    void should_publishNewsPublishedEvent_when_morningRunSavedArticles() {
        // Arrange
        when(newsDataService.updateNews()).thenReturn(3);
        publisherAvailable();

        // Act
        scheduler.runMorningNewsUpdate();

        // Assert
        ArgumentCaptor<NewsPublishedEvent> captor = ArgumentCaptor.forClass(NewsPublishedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo("scheduled-news-morning");
    }

    @Test
    void should_notPublishEvent_when_noArticlesWereSaved() {
        // Arrange
        when(newsDataService.updateNews()).thenReturn(0);

        // Act
        scheduler.runAfternoonNewsUpdate();

        // Assert
        verify(events, never()).ifAvailable(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void should_notPublishEvent_when_publisherUnavailable() {
        // Arrange
        when(newsDataService.updateNews()).thenReturn(5);

        // Act
        scheduler.runEveningNewsUpdate();

        // Assert
        verify(events).ifAvailable(any());
        verify(publisher, never()).publish(any());
    }

    @ParameterizedTest
    @CsvSource({
            "MORNING, scheduled-news-morning",
            "AFTERNOON, scheduled-news-afternoon",
            "EVENING, scheduled-news-evening"
    })
    void should_runTrackedUnderSlotTaskType_when_slotTriggered(String slot, String expectedTaskType) {
        // Arrange
        when(newsDataService.updateNews()).thenReturn(0);

        // Act
        switch (slot) {
            case "MORNING" -> scheduler.runMorningNewsUpdate();
            case "AFTERNOON" -> scheduler.runAfternoonNewsUpdate();
            default -> scheduler.runEveningNewsUpdate();
        }

        // Assert
        verify(taskTracker).runTracked(eq(expectedTaskType), anyString(), any(Runnable.class));
    }
}
