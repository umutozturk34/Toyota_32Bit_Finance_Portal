package com.finance.backend.scheduler;

import com.finance.backend.service.NewsDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class NewsScheduler {

    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;

    @Scheduled(cron = "0 0 10 * * *", zone = "${app.timezone}")
    public void runMorningNewsUpdate() {
        executeUpdate("scheduled-news-morning", "Scheduled morning news update (10:00)");
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "${app.timezone}")
    public void runEveningNewsUpdate() {
        executeUpdate("scheduled-news-evening", "Scheduled evening news update (21:30)");
    }

    private void executeUpdate(String taskType, String description) {
        TaskInfo started = taskTracker.startTask(taskType, description);
        try {
            newsDataService.updateNews();
            taskTracker.completeTask(taskType, started);
        } catch (Exception e) {
            taskTracker.failTask(taskType, started, e.getMessage());
            log.error("{} failed", taskType, e);
        }
    }
}
