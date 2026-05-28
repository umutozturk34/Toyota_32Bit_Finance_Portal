package com.finance.news.service.article;

import com.finance.news.service.source.NewsSourceProcessingService;
import com.finance.news.service.source.NewsSourceService;

import com.finance.news.service.article.NewsDataService;


import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.news.model.NewsSource;
import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates a full news refresh across all enabled sources, processing each in batch. Tolerates
 * per-source failures but aborts early when the RSS circuit breaker opens; fails the run only when no
 * source succeeds. Returns the total number of newly saved articles.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NewsDataService {

    private final NewsSourceProcessingService newsSourceProcessingService;
    private final NewsSourceService newsSourceService;

    /** Refreshes all enabled sources; throws if none are configured or every source fails, else returns articles saved. */
    public int updateNews() {
        List<NewsSource> sources = newsSourceService.getEnabledSources();
        log.info("Starting news update from {} sources", sources.size());

        if (sources.isEmpty()) {
            throw new BusinessException("error.news.noSourcesConfigured");
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
            throw new BusinessException("error.news.allSourcesFailed", sources.size());
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

        return totalSaved[0];
    }
}
