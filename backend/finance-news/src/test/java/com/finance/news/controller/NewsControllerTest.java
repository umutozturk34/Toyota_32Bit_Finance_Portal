package com.finance.news.controller;

import com.finance.common.config.AppProperties;
import com.finance.common.dto.ApiResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.common.i18n.Translator;
import com.finance.news.dto.response.NewsArticleDetailResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.service.article.NewsQueryService;
import com.finance.shared.dto.response.GroupCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsControllerTest {

    @Mock private AppProperties appProperties;
    @Mock private NewsQueryService newsQueryService;
    @Mock private Translator translator;

    private NewsController controller;

    @BeforeEach
    void setUp() {
        controller = new NewsController(appProperties, newsQueryService, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
        AppProperties.Pagination pagination = new AppProperties.Pagination();
        when(appProperties.getPagination()).thenReturn(pagination);
    }

    private NewsArticleResponse sample() {
        return new NewsArticleResponse(1L, "title", "desc", "Reuters", "WORLD",
                LocalDateTime.now(), null);
    }

    @Test
    void getNews_usesDefaultSize_whenSizeNotProvided() {
        PagedResponse<NewsArticleResponse> page = PagedResponse.of(List.of(sample()), 0, 10, 1);
        when(newsQueryService.search(null, null, null, "desc", 0, 10)).thenReturn(page);

        ApiResponse<PagedResponse<NewsArticleResponse>> response =
                controller.getNews(null, null, null, "desc", 0, null);

        assertThat(response.getData().content()).hasSize(1);
    }

    @Test
    void getNews_clampsAboveMaxSize_toMaxValue() {
        PagedResponse<NewsArticleResponse> page = PagedResponse.of(List.of(), 0, 100, 0);
        when(newsQueryService.search(null, null, null, "desc", 0, 100)).thenReturn(page);

        controller.getNews(null, null, null, "desc", 0, 999);

        verify(newsQueryService).search(null, null, null, "desc", 0, 100);
    }

    @Test
    void getNews_clampsBelowOne_toOne() {
        PagedResponse<NewsArticleResponse> page = PagedResponse.of(List.of(), 0, 1, 0);
        when(newsQueryService.search(null, null, null, "desc", 0, 1)).thenReturn(page);

        controller.getNews(null, null, null, "desc", 0, 0);

        verify(newsQueryService).search(null, null, null, "desc", 0, 1);
    }

    @Test
    void getCategoryCounts_delegatesToService() {
        when(newsQueryService.getCategoryCounts())
                .thenReturn(List.of(new GroupCount("WORLD", 5L)));

        ApiResponse<List<GroupCount>> response = controller.getCategoryCounts();

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getNewsById_delegatesToService_andReturnsDetail() {
        NewsArticleDetailResponse detail = new NewsArticleDetailResponse(
                7L, "title", "https://x", "desc", "content", "Reuters", "WORLD",
                LocalDateTime.now(), null);
        when(newsQueryService.getById(7L)).thenReturn(detail);

        ApiResponse<NewsArticleDetailResponse> response = controller.getNewsById(7L);

        assertThat(response.getData().id()).isEqualTo(7L);
    }
}
