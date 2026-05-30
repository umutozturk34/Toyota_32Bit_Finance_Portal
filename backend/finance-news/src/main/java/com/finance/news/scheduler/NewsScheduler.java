package com.finance.news.scheduler;

import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.NewsPublishedEvent;
import com.finance.shared.service.TaskTrackingService;
import com.finance.news.service.article.NewsDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers news ingestion three times daily (morning/afternoon/evening) on configurable crons. Each
 * run is tracked as a task and, only when new articles were saved, publishes a {@link NewsPublishedEvent}
 * to Kafka so downstream consumers can react.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class NewsScheduler {

    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<EventPublisherPort> events;

    @Scheduled(cron = "${app.scheduler.news.morning-cron}", zone = "${app.timezone}")
    public void runMorningNewsUpdate() {
        executeUpdate("scheduled-news-morning", "Scheduled morning news update (08:00)");
    }

    @Scheduled(cron = "${app.scheduler.news.afternoon-cron}", zone = "${app.timezone}")
    public void runAfternoonNewsUpdate() {
        executeUpdate("scheduled-news-afternoon", "Scheduled afternoon news update (14:30)");
    }

    @Scheduled(cron = "${app.scheduler.news.evening-cron}", zone = "${app.timezone}")
    public void runEveningNewsUpdate() {
        executeUpdate("scheduled-news-evening", "Scheduled evening news update (21:30)");
    }

    /** Runs a tracked news update and emits a {@link NewsPublishedEvent} only if at least one new article was saved. */
    private void executeUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            int saved = newsDataService.updateNews();
            if (saved > 0) {
                events.ifAvailable(port -> port.publish(NewsPublishedEvent.of(taskType)));
            }
        });
    }
}
