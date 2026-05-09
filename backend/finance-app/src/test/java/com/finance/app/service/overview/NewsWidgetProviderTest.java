package com.finance.app.service.overview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.app.dto.response.overview.NewsData;
import com.finance.app.dto.response.overview.WidgetKind;
import com.finance.app.dto.response.overview.WidgetSection;
import com.finance.common.dto.response.PagedResponse;
import com.finance.news.dto.response.NewsArticleResponse;
import com.finance.news.service.article.NewsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsWidgetProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NewsQueryService newsQueryService;
    private NewsWidgetProvider provider;

    @BeforeEach
    void setUp() {
        newsQueryService = mock(NewsQueryService.class);
        provider = new NewsWidgetProvider(newsQueryService, new OverviewDefaults(OverviewPropertiesFixture.standard()));
    }

    private NewsArticleResponse article(Long id, String category, LocalDateTime publishedAt) {
        return new NewsArticleResponse(id, "Title-" + id, "Body", "Source", category, publishedAt, "img.jpg");
    }

    private PagedResponse<NewsArticleResponse> pageOf(NewsArticleResponse... items) {
        return PagedResponse.of(List.of(items), 0, items.length, items.length);
    }

    private WidgetSection sectionFor(String configJson) throws Exception {
        JsonNode node = objectMapper.readTree(configJson);
        return new WidgetSection("news", WidgetKind.NEWS, 0, node);
    }

    @Test
    void should_reportNewsKind_when_kindQueried() {
        WidgetKind kind = provider.kind();

        assertThat(kind).isEqualTo(WidgetKind.NEWS);
    }

    @Test
    void should_useDefaultCount_when_configMissingCount() throws Exception {
        when(newsQueryService.search(isNull(), isNull(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(pageOf(article(1L, "MAKRO", LocalDateTime.now())));

        NewsData data = provider.fetch("user-1", sectionFor("{}"));

        assertThat(data.items()).hasSize(1);
        verify(newsQueryService).search(isNull(), isNull(), anyString(), anyString(), eq(0), eq(10));
    }

    @Test
    void should_capAtMaxCount_when_configRequestsTooMany() throws Exception {
        when(newsQueryService.search(any(), any(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(pageOf());

        provider.fetch("user-1", sectionFor("{\"count\":50}"));

        verify(newsQueryService).search(isNull(), isNull(), anyString(), anyString(), eq(0), eq(12));
    }

    @Test
    void should_querySingleCategory_when_oneCategoryProvided() throws Exception {
        when(newsQueryService.search(eq("MAKRO"), isNull(), anyString(), anyString(), eq(0), eq(4)))
                .thenReturn(pageOf(article(1L, "MAKRO", LocalDateTime.now())));

        NewsData data = provider.fetch("user-1", sectionFor("{\"categories\":[\"MAKRO\"],\"count\":4}"));

        assertThat(data.categoriesUsed()).containsExactly("MAKRO");
        assertThat(data.items()).hasSize(1);
    }

    @Test
    void should_aggregateAndDedupe_when_multipleCategoriesProvided() throws Exception {
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(10);
        LocalDateTime t2 = LocalDateTime.now();
        when(newsQueryService.search(eq("MAKRO"), isNull(), anyString(), anyString(), eq(0), eq(4)))
                .thenReturn(pageOf(article(2L, "MAKRO", t2), article(1L, "MAKRO", t1)));
        when(newsQueryService.search(eq("HISSE"), isNull(), anyString(), anyString(), eq(0), eq(4)))
                .thenReturn(pageOf(article(2L, "HISSE", t2), article(3L, "HISSE", t1)));

        NewsData data = provider.fetch("user-1", sectionFor("{\"categories\":[\"MAKRO\",\"HISSE\"],\"count\":4}"));

        assertThat(data.items()).hasSize(3);
        assertThat(data.items()).extracting(NewsData.NewsRow::id).containsExactly(2L, 1L, 3L);
        verify(newsQueryService, times(2)).search(any(), any(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void should_respectUserCategoryOrder_when_categoriesProvided() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(newsQueryService.search(eq("HISSE"), isNull(), anyString(), anyString(), eq(0), eq(2)))
                .thenReturn(pageOf(article(10L, "HISSE", now)));
        when(newsQueryService.search(eq("MAKRO"), isNull(), anyString(), anyString(), eq(0), eq(2)))
                .thenReturn(pageOf(article(20L, "MAKRO", now.plusMinutes(60))));

        NewsData data = provider.fetch("user-1", sectionFor("{\"categories\":[\"HISSE\",\"MAKRO\"],\"count\":2}"));

        assertThat(data.items()).extracting(NewsData.NewsRow::id).containsExactly(10L, 20L);
    }

    @Test
    void should_skipFailingCategory_when_searchThrowsRuntimeException() throws Exception {
        when(newsQueryService.search(eq("BAD"), isNull(), anyString(), anyString(), eq(0), eq(3)))
                .thenThrow(new RuntimeException("invalid category"));
        when(newsQueryService.search(eq("MAKRO"), isNull(), anyString(), anyString(), eq(0), eq(3)))
                .thenReturn(pageOf(article(1L, "MAKRO", LocalDateTime.now())));

        NewsData data = provider.fetch("user-1", sectionFor("{\"categories\":[\"BAD\",\"MAKRO\"],\"count\":3}"));

        assertThat(data.items()).hasSize(1);
        assertThat(data.items()).extracting(NewsData.NewsRow::id).containsExactly(1L);
    }

    @Test
    void should_querySingleAllCategories_when_categoriesEmpty() throws Exception {
        when(newsQueryService.search(isNull(), isNull(), anyString(), anyString(), eq(0), eq(10)))
                .thenReturn(pageOf());

        provider.fetch("user-1", sectionFor("{\"categories\":[]}"));

        verify(newsQueryService, times(1)).search(isNull(), isNull(), anyString(), anyString(), eq(0), eq(10));
    }
}
