package com.finance.news.service;

import com.finance.news.service.article.NewsCacheService;
import com.finance.news.service.article.NewsQueryService;


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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        return new NewsArticleResponse(1L, title, null, "Test", "CRYPTO", LocalDateTime.now(), null, java.util.List.of());
    }

    @Test
    void searchReturnsPaginatedResults() {
        NewsArticle a = article("Bitcoin surges", NewsCategory.CRYPTO);
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1));
        when(responseMapper.toResponses(List.of(a))).thenReturn(List.of(response("Bitcoin surges")));

        PagedResponse<NewsArticleResponse> result = service.search(
                null, null, null, "publishedAt", "desc", 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }

    @Test
    void searchPassesSortParametersToRepository() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search(null, null, null, "title", "asc", 2, 5);

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

        service.search(null, null, null, null, null, 0, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getSort().getOrderFor("publishedAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("publishedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void should_applyCategoryFilter_when_searchCalledWithCategory() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search("CRYPTO", null, null, "publishedAt", "desc", 0, 10);

        verify(articleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_applySearchTermFilter_when_searchCalledWithTerm() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search(null, "bitcoin", null, "publishedAt", "desc", 0, 10);

        verify(articleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_combineCategoryAndSearchTerm_when_bothProvided() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search("CRYPTO", "ethereum", null, "publishedAt", "desc", 0, 10);

        verify(articleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_treatBlankCategoryAsAbsent_when_searchCalled() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search("   ", "  ", null, "publishedAt", "desc", 0, 10);

        verify(articleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_returnDetailResponse_when_getByIdFindsArticle() {
        NewsArticle article = article("Bitcoin", NewsCategory.CRYPTO);
        com.finance.news.dto.response.NewsArticleDetailResponse detail =
                new com.finance.news.dto.response.NewsArticleDetailResponse(
                        1L, "Bitcoin", "https://x.com", "summary", "content",
                        "Test", "CRYPTO", LocalDateTime.now(), null, java.util.List.of());
        when(newsCacheService.getById(1L)).thenReturn(java.util.Optional.of(article));
        when(responseMapper.toDetailResponse(article)).thenReturn(detail);

        com.finance.news.dto.response.NewsArticleDetailResponse out = service.getById(1L);

        assertThat(out.id()).isEqualTo(1L);
    }

    @Test
    void should_throwResourceNotFound_when_getByIdMissesCache() {
        when(newsCacheService.getById(404L)).thenReturn(java.util.Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getById(404L))
                .isInstanceOf(com.finance.common.exception.ResourceNotFoundException.class)
                .hasMessageContaining("error.news.articleNotFound");
    }

    @Test
    void should_mapCategoryCountsFromRepositoryRows_when_getCategoryCountsCalled() {
        when(articleRepository.countByCategory()).thenReturn(List.of(
                new Object[]{NewsCategory.CRYPTO, 12L},
                new Object[]{NewsCategory.BORSA_ISTANBUL, 5L}));

        List<com.finance.shared.dto.response.GroupCount> counts = service.getCategoryCounts();

        assertThat(counts).hasSize(2);
        assertThat(counts.get(0).count()).isEqualTo(12L);
    }

    @Test
    void should_mapAssetCountsFromRepositoryRows_when_getAssetCountsCalled() {
        when(articleRepository.countArticlesByAsset()).thenReturn(List.of(
                new Object[]{"THYAO.IS", "STOCK", 9L},
                new Object[]{"BTC-USD", "CRYPTO", 4L}));

        var result = service.getAssetCounts(10);

        assertThat(result.assets()).hasSize(2);
        assertThat(result.totalMentions()).isEqualTo(13L);
        assertThat(result.assets().get(0).code()).isEqualTo("THYAO.IS");
        assertThat(result.assets().get(0).type()).isEqualTo("STOCK");
        assertThat(result.assets().get(0).count()).isEqualTo(9L);
        assertThat(result.assets().get(1).code()).isEqualTo("BTC-USD");
        assertThat(result.assets().get(1).count()).isEqualTo(4L);
    }

    @Test
    void should_truncateAssetCountsToLimit_when_moreRowsThanLimit() {
        when(articleRepository.countArticlesByAsset()).thenReturn(List.of(
                new Object[]{"THYAO.IS", "STOCK", 9L},
                new Object[]{"BTC-USD", "CRYPTO", 4L},
                new Object[]{"EREGL.IS", "STOCK", 2L}));

        var result = service.getAssetCounts(2);

        assertThat(result.assets()).hasSize(2);
        // Total includes the truncated tail (EREGL's 2), proving the share denominator isn't the capped sum.
        assertThat(result.totalMentions()).isEqualTo(15L);
        assertThat(result.assets().get(0).code()).isEqualTo("THYAO.IS");
        assertThat(result.assets().get(1).code()).isEqualTo("BTC-USD");
    }

    @Test
    void should_returnEmptyAssetCounts_when_noRows() {
        when(articleRepository.countArticlesByAsset()).thenReturn(List.of());

        var result = service.getAssetCounts(10);

        assertThat(result.assets()).isEmpty();
        assertThat(result.totalMentions()).isEqualTo(0L);
    }

    @Test
    void should_returnEmptyCategoryCounts_when_noRows() {
        when(articleRepository.countByCategory()).thenReturn(List.of());

        List<com.finance.shared.dto.response.GroupCount> result = service.getCategoryCounts();

        assertThat(result).isEmpty();
    }

    @Test
    void should_rejectUnknownCategory_when_searchCalledWithInvalidCategory() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.search("NOT_A_CATEGORY", null, null, "publishedAt", "desc", 0, 10))
                .isInstanceOf(com.finance.common.exception.BadRequestException.class);

        verify(articleRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void should_applyAssetCodeFilter_when_searchCalledWithAssetCode() {
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        service.search(null, null, "THYAO.IS", "publishedAt", "desc", 0, 10);

        Specification<NewsArticle> spec = captureSpecification();
        Path<Object> codePath = invokeSpec(spec);
        verify(codePath).in(List.of("THYAO.IS"));
    }

    @Test
    void should_splitCommaSeparatedAssetCodes_when_searchCalledWithMultipleAssets() {
        // Arrange: assetCode carries duplicates, blanks and padding around the commas; the spec must
        // de-dupe/trim down to the two distinct codes and feed exactly those to the IN clause.
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        // Act
        service.search(null, null, " THYAO.IS , BTC-USD , THYAO.IS , ", "publishedAt", "desc", 0, 10);

        // Assert
        Specification<NewsArticle> spec = captureSpecification();
        ArgumentCaptor<List<String>> codesCaptor = ArgumentCaptor.forClass(List.class);
        invokeSpecCapturingInCodes(spec, codesCaptor);
        assertThat(codesCaptor.getValue()).containsExactly("THYAO.IS", "BTC-USD");
    }

    @Test
    void should_ignoreAssetCodeFilter_when_onlyBlankTokensSupplied() {
        // Arrange: a comma-string of nothing but blanks must collapse to no codes, so the asset join
        // is never added (distinct never toggled) — leaving only the always-true conjunction.
        when(articleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toResponses(List.of())).thenReturn(List.of());

        // Act
        service.search(null, null, " , , ", "publishedAt", "desc", 0, 10);

        // Assert
        Specification<NewsArticle> spec = captureSpecification();
        Root<NewsArticle> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        spec.toPredicate((Root) root, query, cb);
        verify(query, never()).distinct(true);
        verify(root, never()).join(anyString());
    }

    @SuppressWarnings("rawtypes")
    private Specification<NewsArticle> captureSpecification() {
        ArgumentCaptor<Specification> captor = ArgumentCaptor.forClass(Specification.class);
        verify(articleRepository).findAll(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    /** Runs the captured spec against mocked criteria objects so the asset-code lambda body executes. */
    private Path<Object> invokeSpec(Specification<NewsArticle> spec) {
        Root<NewsArticle> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Join<Object, Object> join = mock(Join.class);
        Path<Object> codePath = mock(Path.class);
        Predicate inPredicate = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(mock(Predicate.class));
        when(root.join("assets")).thenReturn(join);
        when(join.get("assetCode")).thenReturn(codePath);
        when(codePath.in(any(List.class))).thenReturn(inPredicate);
        spec.toPredicate((Root) root, query, cb);
        verify(query).distinct(true);
        return codePath;
    }

    private void invokeSpecCapturingInCodes(Specification<NewsArticle> spec,
                                            ArgumentCaptor<List<String>> codesCaptor) {
        Root<NewsArticle> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Join<Object, Object> join = mock(Join.class);
        Path<Object> codePath = mock(Path.class);
        when(root.join("assets")).thenReturn(join);
        when(join.get("assetCode")).thenReturn(codePath);
        when(codePath.in(any(List.class))).thenReturn(mock(Predicate.class));
        spec.toPredicate((Root) root, query, cb);
        verify(codePath).in(codesCaptor.capture());
        verify(query).distinct(true);
    }
}
