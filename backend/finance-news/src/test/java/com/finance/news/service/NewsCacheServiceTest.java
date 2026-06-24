package com.finance.news.service;

import com.finance.news.service.article.NewsCacheService;

import tools.jackson.databind.ObjectMapper;
import com.finance.news.config.NewsProperties;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsArticleAsset;
import com.finance.news.model.NewsCategory;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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
        objectMapper = new ObjectMapper();
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
                .source(NewsSource.builder().id(1L).name("Test").url("https://example.com").build())
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

    @Test
    void cacheArticleCachesTaggedArticleAsLinkedHashSetCopy_withoutMutatingEntity() {
        // Arrange: a tagged article whose `assets` is NOT a LinkedHashSet (a HashSet stands in for Hibernate's
        // PersistentSet). It used to be skipped entirely; it must now cache a detached copy.
        NewsArticle a = article(4L, "Akbank");
        Set<NewsArticleAsset> hibernateLikeSet = new HashSet<>();
        hibernateLikeSet.add(new NewsArticleAsset("AKBNK.IS", "STOCK"));
        a.setAssets(hibernateLikeSet);

        // Act
        service.cacheArticle(a);

        // Assert: it IS cached (not skipped), the cached copy carries a plain LinkedHashSet with the same asset, and
        // the managed entity's own collection is left untouched (no risk of a stray DB write).
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(valueOps).set(eq("news:article:4"), captor.capture(), any(Duration.class));
        NewsArticle cached = (NewsArticle) captor.getValue();
        assertThat(cached).isNotSameAs(a);
        assertThat(cached.getAssets()).isInstanceOf(LinkedHashSet.class);
        assertThat(cached.getAssets()).extracting(NewsArticleAsset::getAssetCode).containsExactly("AKBNK.IS");
        assertThat(a.getAssets()).isSameAs(hibernateLikeSet);
    }
}
