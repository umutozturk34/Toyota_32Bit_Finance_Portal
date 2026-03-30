package com.finance.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.config.AppProperties;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import com.finance.backend.repository.NewsArticleRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@Log4j2
public class NewsCacheService {

    private static final String CACHE_LATEST = "news:latest";
    private static final String CACHE_ARTICLE = "news:article:";
    private static final int DEFAULT_CATEGORY_LIMIT = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NewsArticleRepository articleRepository;
    private final Duration cacheTtl;
    private final Map<String, Integer> categoryLimits;

    public NewsCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            NewsArticleRepository articleRepository,
            AppProperties appProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.articleRepository = articleRepository;
        this.cacheTtl = Duration.ofHours(appProperties.getNews().getCacheTtlHours());
        this.categoryLimits = appProperties.getNews().getCategoryLimits();
    }

    public List<NewsArticle> getByCategory(NewsCategory category) {
        int limit = categoryLimits.getOrDefault(category.name(), DEFAULT_CATEGORY_LIMIT);
        return articleRepository.findTopByCategoryOrderByPublishedAtDesc(category, limit);
    }

    public List<NewsArticle> getLatest() {
        List<NewsArticle> cached = readListFromCache(CACHE_LATEST);
        if (cached != null) {
            return cached;
        }

        List<NewsArticle> articles = buildAggregatedLatest();
        writeToCache(CACHE_LATEST, articles);
        return articles;
    }

    public Optional<NewsArticle> getById(Long id) {
        String key = CACHE_ARTICLE + id;
        NewsArticle cached = readArticleFromCache(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<NewsArticle> article = articleRepository.findById(id);
        article.ifPresent(a -> writeToCache(key, a));
        return article;
    }

    public void cacheArticle(NewsArticle article) {
        writeToCache(CACHE_ARTICLE + article.getId(), article);
    }

    public void refreshAll() {
        List<NewsArticle> aggregated = buildAggregatedLatest();
        writeToCache(CACHE_LATEST, aggregated);

        log.info("News latest cache refreshed: {} total articles, TTL={}h",
                aggregated.size(), cacheTtl.toHours());
    }

    private NewsArticle readArticleFromCache(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return null;
        }
        return objectMapper.convertValue(cached, NewsArticle.class);
    }

    private List<NewsArticle> readListFromCache(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            return null;
        }
        return objectMapper.convertValue(cached, new TypeReference<List<NewsArticle>>() {});
    }

    private void writeToCache(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, cacheTtl);
    }

    private List<NewsArticle> buildAggregatedLatest() {
        List<NewsArticle> aggregated = new ArrayList<>();
        for (NewsCategory category : NewsCategory.values()) {
            int limit = categoryLimits.getOrDefault(category.name(), DEFAULT_CATEGORY_LIMIT);
            List<NewsArticle> articles = articleRepository.findTopByCategoryOrderByPublishedAtDesc(category, limit);
            aggregated.addAll(articles);
        }
        aggregated.sort(Comparator.comparing(NewsArticle::getPublishedAt).reversed());
        return aggregated;
    }
}