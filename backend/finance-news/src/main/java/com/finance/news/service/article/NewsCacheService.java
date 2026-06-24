package com.finance.news.service.article;

import com.finance.news.service.article.NewsCacheService;

import com.finance.news.config.NewsProperties;
import com.finance.news.model.NewsArticle;
import com.finance.news.repository.NewsArticleRepository;
import com.finance.shared.util.RedisKeys;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;

/** Read-through Redis cache for single articles keyed by id, with a configurable TTL; misses load from the DB and backfill the cache. */
@Service
@Log4j2
public class NewsCacheService {

    private static final String CACHE_ARTICLE = RedisKeys.NEWS_ARTICLE;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final NewsArticleRepository articleRepository;
    private final Duration cacheTtl;

    public NewsCacheService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper,
            NewsArticleRepository articleRepository,
            NewsProperties newsProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.articleRepository = articleRepository;
        this.cacheTtl = Duration.ofHours(newsProperties.getCacheTtlHours());
    }

    /** Returns the cached article, falling back to the repository and caching the result on a miss. */
    public Optional<NewsArticle> getById(Long id) {
        String key = CACHE_ARTICLE + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return Optional.of(objectMapper.convertValue(cached, NewsArticle.class));
            } catch (RuntimeException e) {
                // A cache entry written before assets were normalised serialises that collection as a Hibernate
                // PersistentSet, whose runtime class can't be deserialised back. Evict the bad entry and fall
                // through to a fresh DB load + re-cache, so one stale article no longer 500s its detail page.
                log.warn("Unreadable cache for article {} ({}); evicting and reloading from DB", id, e.getMessage());
                redisTemplate.delete(key);
            }
        }
        Optional<NewsArticle> article = articleRepository.findById(id);
        article.ifPresent(this::cacheArticle);
        return article;
    }

    /** Writes (or overwrites) the article in Redis under its id key with the configured TTL. */
    public void cacheArticle(NewsArticle article) {
        // Hibernate hands `assets` to us as a PersistentSet; the typed Redis serializer would write its runtime
        // class (org.hibernate.collection.spi.PersistentSet), which deserialisation can't instantiate. We must NOT
        // mutate the (possibly managed) entity to normalise the collection — that risks a DB write. Instead cache a
        // detached SHALLOW copy whose `assets` is a fresh LinkedHashSet, so tagged articles (most of them) cache too
        // instead of forever hitting the DB on the detail page. The lazy `source` is @JsonIgnore'd out of the cache,
        // so it is intentionally not copied (touching it could throw LazyInitializationException).
        redisTemplate.opsForValue().set(CACHE_ARTICLE + article.getId(), detachedCopy(article), cacheTtl);
    }

    /** A serialisation-safe shallow copy with `assets` rehomed into a plain {@link LinkedHashSet}; never mutates {@code article}. */
    private static NewsArticle detachedCopy(NewsArticle article) {
        return NewsArticle.builder()
                .id(article.getId())
                .link(article.getLink())
                .title(article.getTitle())
                .description(article.getDescription())
                .category(article.getCategory())
                .publishedAt(article.getPublishedAt())
                .fetchedAt(article.getFetchedAt())
                .imageUrl(article.getImageUrl())
                .content(article.getContent())
                .guid(article.getGuid())
                .assets(article.getAssets() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(article.getAssets()))
                .build();
    }
}
