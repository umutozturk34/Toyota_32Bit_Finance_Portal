package com.finance.news.scheduler;

import com.finance.common.event.MarketUpdateEventPort;
import com.finance.common.model.MarketType;
import com.finance.common.service.TaskTrackingService;
import com.finance.news.service.article.NewsDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class NewsScheduler {

    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final ObjectProvider<MarketUpdateEventPort> marketEvents;

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

    private void executeUpdate(String taskType, String description) {
        taskTracker.runTracked(taskType, description, () -> {
            newsDataService.updateNews();
            marketEvents.ifAvailable(port -> port.publishMarketUpdated(MarketType.NEWS, taskType));
        });
    }
}
