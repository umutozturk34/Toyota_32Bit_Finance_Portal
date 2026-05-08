package com.finance.news.service.article;

import com.finance.news.service.source.*;

import com.finance.news.service.article.*;


import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.news.model.NewsSource;
import com.finance.common.util.BatchLogHelper;
import com.finance.common.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class NewsDataService {

    private final NewsSourceProcessingService newsSourceProcessingService;
    private final NewsSourceService newsSourceService;

    public void updateNews() {
        List<NewsSource> sources = newsSourceService.getEnabledSources();
        log.info("Starting news update from {} sources", sources.size());

        if (sources.isEmpty()) {
            throw new BusinessException("No news sources configured");
        }

        final int[] totalSaved = {0};

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                sources,
                source -> {
                    int saved = newsSourceProcessingService.processSource(source);
                    totalSaved[0] += saved;
                    log.info("Source {} processed: {} new articles saved", source.getName(), saved);
                },
                NewsSource::getName,
                "news update",
                5,
                (source, e) -> {
                    if (e instanceof ExternalApiException) {
                        log.error("External API error for source {}: {}", source.getName(), e.getMessage(), e);
                    } else if (e instanceof BusinessException) {
                        log.error("Business error for source {}: {}", source.getName(), e.getMessage(), e);
                    } else {
                        log.error("Unexpected error for source {}: {}", source.getName(), e.getMessage(), e);
                    }
                },
                e -> e instanceof CallNotPermittedException,
                (stopped, e) -> log.warn("Circuit breaker OPEN for RSS feeds, stopping news update"));

        if (result.successCount() == 0) {
            throw new BusinessException("All " + sources.size() + " news sources failed: " + result.failedItems());
        }

        if (totalSaved[0] > 0) {
            log.info("Saved {} new articles", totalSaved[0]);
        } else if (result.failCount() == 0) {
            log.info("No new articles found across all sources");
        }

        BatchLogHelper.logSummaryWithMetric(
                log,
                "News update completed",
                result,
                "new articles",
                totalSaved[0]);
    }
}
