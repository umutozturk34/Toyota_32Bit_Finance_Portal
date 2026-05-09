package com.finance.news.service;

import com.finance.news.service.source.NewsSourceProcessingService;

import com.finance.news.service.article.NewsCacheService;

import com.finance.news.client.RssClient;
import com.finance.news.config.NewsProperties;
import com.finance.news.dto.external.NewsArticleDto;
import com.finance.news.dto.internal.RssArticleData;
import com.finance.common.exception.BusinessException;
import com.finance.news.mapper.NewsArticleMapper;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSourceProcessingServiceTest {

    private RssClient rssClient;
    private NewsArticleMapper articleMapper;
    private NewsArticleRepository articleRepository;
    private NewsCacheService cacheService;
    private NewsSourceProcessingService service;

    @BeforeEach
    void setUp() {
        rssClient = mock(RssClient.class);
        articleMapper = mock(NewsArticleMapper.class);
        articleRepository = mock(NewsArticleRepository.class);
        cacheService = mock(NewsCacheService.class);
        TransactionTemplate txTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        NewsProperties props = new NewsProperties();
        props.setMaxArticlesPerSource(10);
        service = new NewsSourceProcessingService(rssClient, articleMapper, articleRepository, cacheService, txTemplate, props);
    }

    private NewsSource source() {
        NewsSource s = new NewsSource();
        s.setId(1L);
        s.setName("BBC");
        s.setUrl("https://bbc.co.uk/rss");
        s.setDefaultCategory(NewsCategory.CRYPTO.name());
        s.setEnabled(true);
        return s;
    }

    private RssArticleData rssItem(String title, String link) {
        return new RssArticleData(title, link, "desc", "content", null, "guid-" + link, LocalDateTime.now());
    }

    private NewsArticleDto articleDto(String title, NewsCategory category) {
        return new NewsArticleDto(title, "https://bbc.co.uk/" + title, "desc", "content", "BBC", "https://bbc.co.uk/rss",
                category, LocalDateTime.now(), null, "guid");
    }

    private NewsArticle articleEntity(Long id, String title) {
        return NewsArticle.builder().id(id).title(title).link("https://bbc.co.uk/" + title)
                .source(NewsSource.builder().id(1L).name("BBC").url("https://bbc.co.uk/rss").build())
                .category(NewsCategory.CRYPTO).publishedAt(LocalDateTime.now()).fetchedAt(LocalDateTime.now()).build();
    }

    @Test
    void processSourceSavesNewArticles() {
        RssArticleData item = rssItem("BTC jumps", "https://bbc.co.uk/btc");
        NewsArticleDto dto = articleDto("BTC jumps", NewsCategory.CRYPTO);
        NewsArticle entity = articleEntity(1L, "BTC jumps");
        when(rssClient.fetchFeed(anyString())).thenReturn(List.of(item));
        when(articleRepository.existsByGuid(any())).thenReturn(false);
        when(articleRepository.existsByLink(any())).thenReturn(false);
        when(articleMapper.toDto(any(), anyString(), anyString(), any())).thenReturn(dto);
        when(articleMapper.toEntity(any(), any())).thenReturn(entity);
        when(articleRepository.save(entity)).thenReturn(entity);

        int saved = service.processSource(source());

        assertThat(saved).isEqualTo(1);
        verify(cacheService).cacheArticle(entity);
    }

    @Test
    void processSourceThrowsWhenFeedEmpty() {
        when(rssClient.fetchFeed(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> service.processSource(source()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no valid articles");
    }

    @Test
    void processSourceSkipsDuplicates() {
        RssArticleData item = rssItem("BTC jumps", "https://bbc.co.uk/btc");
        when(rssClient.fetchFeed(anyString())).thenReturn(List.of(item));
        when(articleRepository.existsByGuid(any())).thenReturn(true);

        int saved = service.processSource(source());

        assertThat(saved).isZero();
        verify(articleRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void processSourceSkipsUncategorizedArticles() {
        RssArticleData item = rssItem("Random news", "https://bbc.co.uk/random");
        when(rssClient.fetchFeed(anyString())).thenReturn(List.of(item));
        when(articleRepository.existsByGuid(any())).thenReturn(false);
        when(articleRepository.existsByLink(any())).thenReturn(false);
        when(articleMapper.toDto(any(), anyString(), anyString(), any())).thenReturn(null);

        int saved = service.processSource(source());

        assertThat(saved).isZero();
        verify(articleRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void processSourceLimitsToMaxArticlesPerSource() {
        NewsProperties props = new NewsProperties();
        props.setMaxArticlesPerSource(2);
        TransactionTemplate txTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        NewsSourceProcessingService limitedService = new NewsSourceProcessingService(
                rssClient, articleMapper, articleRepository, cacheService, txTemplate, props);
        List<RssArticleData> items = List.of(
                rssItem("A", "https://bbc.co.uk/a"),
                rssItem("B", "https://bbc.co.uk/b"),
                rssItem("C", "https://bbc.co.uk/c"));
        when(rssClient.fetchFeed(anyString())).thenReturn(items);
        when(articleRepository.existsByGuid(any())).thenReturn(false);
        when(articleRepository.existsByLink(any())).thenReturn(false);
        when(articleMapper.toDto(any(), anyString(), anyString(), any())).thenReturn(articleDto("x", NewsCategory.CRYPTO));
        NewsArticle entity = articleEntity(1L, "x");
        when(articleMapper.toEntity(any(), any())).thenReturn(entity);
        when(articleRepository.save(any())).thenReturn(entity);

        int saved = limitedService.processSource(source());

        assertThat(saved).isEqualTo(2);
    }

    @Test
    void processSourceThrowsWhenAllSavesFail() {
        RssArticleData item = rssItem("BTC", "https://bbc.co.uk/btc");
        when(rssClient.fetchFeed(anyString())).thenReturn(List.of(item));
        when(articleRepository.existsByGuid(any())).thenReturn(false);
        when(articleRepository.existsByLink(any())).thenReturn(false);
        when(articleMapper.toDto(any(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("mapping failed"));

        assertThatThrownBy(() -> service.processSource(source()))
                .isInstanceOf(BusinessException.class);
    }
}
