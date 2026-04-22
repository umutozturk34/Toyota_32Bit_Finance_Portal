package com.finance.backend.service;

import com.finance.backend.client.RssClient;
import com.finance.backend.dto.external.NewsArticleDto;
import com.finance.backend.dto.internal.RssArticleData;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.NewsArticleMapper;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsSource;
import com.finance.backend.repository.NewsArticleRepository;
import com.finance.backend.util.BatchFailureGuard;
import com.finance.backend.util.NewsDuplicateChecker;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
public class NewsSourceProcessingService {

    private final RssClient rssClient;
    private final NewsArticleMapper articleMapper;
    private final NewsArticleRepository articleRepository;
    private final NewsCacheService newsCacheService;
    private final TransactionTemplate transactionTemplate;
    private final int maxArticlesPerSource;

    public NewsSourceProcessingService(RssClient rssClient,
                                       NewsArticleMapper articleMapper,
                                       NewsArticleRepository articleRepository,
                                       NewsCacheService newsCacheService,
                                       TransactionTemplate transactionTemplate,
                                       com.finance.backend.config.AppProperties appProperties) {
        this.rssClient = rssClient;
        this.articleMapper = articleMapper;
        this.articleRepository = articleRepository;
        this.newsCacheService = newsCacheService;
        this.transactionTemplate = transactionTemplate;
        this.maxArticlesPerSource = appProperties.getNews().getMaxArticlesPerSource();
    }

    public int processSource(NewsSource source) {
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
                log.debug("Skipping duplicate article: '{}' [{}]", data.title(), data.link());
                continue;
            }

            try {
                NewsArticleDto dto = articleMapper.toDto(
                        data, source.getName(), source.getUrl(), source.getDefaultCategory());
                if (dto == null) {
                    log.debug("Skipping uncategorized article from {}: '{}'", source.getName(), data.title());
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
