package com.finance.backend.config;

import com.finance.backend.repository.NewsArticleRepository;
import com.finance.backend.service.NewsDataService;
import com.finance.backend.service.TaskTrackingService;
import com.finance.backend.service.TaskTrackingService.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Log4j2
@Component
@Order(6)
@RequiredArgsConstructor
public class NewsDataInitializer implements CommandLineRunner {

    private final NewsArticleRepository articleRepository;
    private final NewsDataService newsDataService;
    private final TaskTrackingService taskTracker;
    private final Executor taskExecutor;

    @Override
    public void run(String... args) {
        long count = articleRepository.count();
        if (count > 0) {
            log.info("News data exists ({} articles) - skipping init", count);
            return;
        }
        log.info("No news data - starting initial RSS fetch");
        TaskInfo started = taskTracker.startTask("init-news", "Initial news feed fetch");
        taskExecutor.execute(() -> {
            try {
                newsDataService.updateNews();
                taskTracker.completeTask("init-news", started);
            } catch (Exception e) {
                taskTracker.failTask("init-news", started, e.getMessage());
                log.error("News init failed", e);
            }
        });
    }
}
