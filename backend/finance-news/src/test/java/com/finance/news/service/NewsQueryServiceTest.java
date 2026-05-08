package com.finance.news.service;

import com.finance.news.service.article.*;


import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.news.mapper.NewsResponseMapper;
import com.finance.news.model.NewsArticle;
import com.finance.news.model.NewsCategory;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class NewsQueryServiceTest {

    @Mock private NewsArticleRepository articleRepository;
    @Mock private NewsCacheService newsCacheService;
    @Mock private NewsResponseMapper responseMapper;

    private NewsQueryService service;

    @BeforeEach
    void setUp() {
        service = new NewsQueryService(articleRepository, newsCacheService, responseMapper);
    }

    private NewsArticle article(String title, NewsCategory category) {
        return NewsArticle.builder()
                .id(1L)
                .title(title)
                .link("https://example.com/" + title.hashCode())
                .source(NewsSource.builder().id(1L).name("Test").url("https://example.com").build())
                .category(category)
                .publishedAt(LocalDateTime.now())
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    private NewsArticleResponse response(String title) {
        return new NewsArticleResponse(1L, title, null, "Test", "CRYPTO", LocalDateTime.now(), null);
    }

    @Test
    void searchReturnsPaginatedResults() {
        NewsArticle a = article("Bitcoin surges", NewsCategory.CRYPTO);
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));
        when(responseMapper.toResponses(List.of(a))).thenReturn(List.of(response("Bitcoin surges")));

        PagedResponse<NewsArticleResponse> result = service.search(
                null, null, "publishedAt", "desc", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }

    @Test
    void searchPassesSortParametersToRepository() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search(null, null, "title", "asc", 2, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void searchWithNoSortDefaultsToPublishedAtDesc() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search(null, null, null, null, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getSort().getOrderFor("publishedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
