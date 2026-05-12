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
import java.util.Optional;

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

    public Optional<NewsArticle> getById(Long id) {
        String key = CACHE_ARTICLE + id;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Optional.of(objectMapper.convertValue(cached, NewsArticle.class));
        }
        Optional<NewsArticle> article = articleRepository.findById(id);
        article.ifPresent(a -> redisTemplate.opsForValue().set(key, a, cacheTtl));
        return article;
    }

    public void cacheArticle(NewsArticle article) {
        redisTemplate.opsForValue().set(CACHE_ARTICLE + article.getId(), article, cacheTtl);
    }
}
