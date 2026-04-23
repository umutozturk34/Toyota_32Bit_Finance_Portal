package com.finance.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finance.backend.config.NewsProperties;
import com.finance.backend.model.NewsArticle;
import com.finance.backend.model.NewsCategory;
import com.finance.backend.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsCacheServiceTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private NewsArticleRepository articleRepository;
    private ObjectMapper objectMapper;
    private NewsCacheService service;

    @BeforeEach
    void setUp() {
        articleRepository = mock(NewsArticleRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        NewsProperties props = new NewsProperties();
        props.setCacheTtlHours(24);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new NewsCacheService(redisTemplate, objectMapper, articleRepository, props);
    }

    private NewsArticle article(Long id, String title) {
        return NewsArticle.builder()
                .id(id)
                .title(title)
                .link("https://example.com/" + id)
                .sourceName("Test")
                .category(NewsCategory.CRYPTO)
                .publishedAt(LocalDateTime.now())
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getByIdReturnsCachedArticleWhenPresent() {
        NewsArticle cached = article(1L, "Bitcoin");
        when(valueOps.get("news:article:1")).thenReturn(cached);

        Optional<NewsArticle> result = service.getById(1L);

        assertThat(result).isPresent();
        verify(articleRepository, never()).findById(any());
    }

    @Test
    void getByIdFetchesFromRepoAndCachesOnMiss() {
        NewsArticle stored = article(2L, "Ethereum");
        when(valueOps.get("news:article:2")).thenReturn(null);
        when(articleRepository.findById(2L)).thenReturn(Optional.of(stored));

        Optional<NewsArticle> result = service.getById(2L);

        assertThat(result).contains(stored);
        verify(valueOps).set(eq("news:article:2"), eq(stored), any(Duration.class));
    }

    @Test
    void getByIdReturnsEmptyWhenNotInCacheAndNotInRepo() {
        when(valueOps.get("news:article:99")).thenReturn(null);
        when(articleRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<NewsArticle> result = service.getById(99L);

        assertThat(result).isEmpty();
        verify(valueOps, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void cacheArticleStoresWithTtl() {
        NewsArticle a = article(3L, "Gold");

        service.cacheArticle(a);

        verify(valueOps).set(eq("news:article:3"), eq(a), any(Duration.class));
    }
}
