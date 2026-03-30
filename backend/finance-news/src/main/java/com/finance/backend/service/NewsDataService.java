package com.finance.backend.service;

import com.finance.backend.client.RssClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.AppProperties.NewsSource;
import com.finance.backend.dto.external.NewsArticleDto;
import com.finance.backend.dto.internal.RssArticleData;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ExternalApiException;
import com.finance.backend.mapper.NewsArticleMapper;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.repository.NewsArticleRepository;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.util.NewsDuplicateChecker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
public class NewsDataService {

    private final RssClient rssClient;
    private final NewsArticleMapper articleMapper;
    private final NewsArticleRepository articleRepository;
    private final NewsCacheService newsCacheService;
    private final TransactionTemplate transactionTemplate;
    private final List<NewsSource> sources;
    private final int maxArticlesPerSource;

    public NewsDataService(RssClient rssClient,
                           NewsArticleMapper articleMapper,
                           NewsArticleRepository articleRepository,
                           NewsCacheService newsCacheService,
                           PlatformTransactionManager transactionManager,
                           AppProperties appProperties) {
        this.rssClient = rssClient;
        this.articleMapper = articleMapper;
        this.articleRepository = articleRepository;
        this.newsCacheService = newsCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.sources = appProperties.getNews().getSources();
        this.maxArticlesPerSource = appProperties.getNews().getMaxArticlesPerSource();
    }

    public void updateNews() {
        log.info("Starting news update from {} sources", sources.size());

        if (sources.isEmpty()) {
            throw new BusinessException("No news sources configured");
        }

        int totalSuccess = 0;
        int totalFailed = 0;
        int totalSaved = 0;
        List<String> failedSources = new ArrayList<>();

        for (NewsSource source : sources) {
            try {
                int saved = processSource(source);
                totalSuccess++;
                totalSaved += saved;
                log.info("Source {} processed: {} new articles saved", source.getName(), saved);
            } catch (CallNotPermittedException e) {
                log.warn("Circuit breaker OPEN for RSS feeds, stopping news update at source {}", source.getName());
                break;
            } catch (ExternalApiException e) {
                totalFailed++;
                failedSources.add(source.getName());
                log.error("External API error for source {}: {}", source.getName(), e.getMessage(), e);
                BatchFailureGuard.check(totalSuccess, totalFailed, failedSources, "news update");
            } catch (BusinessException e) {
                totalFailed++;
                failedSources.add(source.getName());
                log.error("Business error for source {}: {}", source.getName(), e.getMessage(), e);
                BatchFailureGuard.check(totalSuccess, totalFailed, failedSources, "news update");
            } catch (Exception e) {
                totalFailed++;
                failedSources.add(source.getName());
                log.error("Unexpected error for source {}: {}", source.getName(), e.getMessage(), e);
                BatchFailureGuard.check(totalSuccess, totalFailed, failedSources, "news update");
            }
        }

        if (totalSuccess == 0) {
            throw new BusinessException("All " + sources.size() + " news sources failed: " + failedSources);
        }

        if (totalSaved > 0) {
            newsCacheService.refreshAll();
        } else if (totalFailed == 0) {
            log.info("No new articles found across all sources");
        }

        log.info("News update completed: {}/{} sources OK, {} failed, {} new articles",
                totalSuccess, sources.size(), totalFailed, totalSaved);
    }

    private int processSource(NewsSource source) {
        List<RssArticleData> rawArticles = rssClient.fetchFeed(source.getUrl());

        if (rawArticles.isEmpty()) {
            throw new BusinessException("Source " + source.getName() + " returned no valid articles after filtering");
        }

        List<RssArticleData> limited = rawArticles.size() > maxArticlesPerSource
                ? rawArticles.subList(0, maxArticlesPerSource)
                : rawArticles;

        LocalDateTime now = LocalDateTime.now();
        int savedCount = 0;
        int failedCount = 0;
        List<String> failedLinks = new ArrayList<>();

        for (RssArticleData data : limited) {
            if (NewsDuplicateChecker.isDuplicate(data, articleRepository)) {
                continue;
            }

            try {
                NewsArticleDto dto = articleMapper.toDto(
                        data, source.getName(), source.getUrl(), source.getDefaultCategory());
                if (dto == null) {
                    continue;
                }
                NewsArticle entity = articleMapper.toEntity(dto, now);
                NewsArticle saved = transactionTemplate.execute(status -> articleRepository.save(entity));
                newsCacheService.cacheArticle(saved);
                savedCount++;
            } catch (Exception e) {
                failedCount++;
                failedLinks.add(data.link());
                log.warn("Failed to save article '{}': {}", data.title(), e.getMessage());
                BatchFailureGuard.check(savedCount, failedCount, failedLinks, "news article save");
            }
        }

        if (savedCount == 0 && failedCount > 0) {
            throw new BusinessException(
                    "All " + failedCount + " new articles failed to save for source " + source.getName());
        }

        return savedCount;
    }
}
