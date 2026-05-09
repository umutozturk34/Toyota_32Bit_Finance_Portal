package com.finance.news.service;

import com.finance.news.service.source.NewsSourceProcessingService;
import com.finance.news.service.source.NewsSourceService;
import com.finance.news.service.article.NewsDataService;



import com.finance.common.exception.BusinessException;
import com.finance.news.model.NewsSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsDataServiceTest {

    private NewsSourceProcessingService processingService;
    private NewsSourceService sourceService;
    private NewsDataService service;

    @BeforeEach
    void setUp() {
        processingService = mock(NewsSourceProcessingService.class);
        sourceService = mock(NewsSourceService.class);
        service = new NewsDataService(processingService, sourceService);
    }

    private NewsSource source(Long id, String name) {
        NewsSource s = new NewsSource();
        s.setId(id);
        s.setName(name);
        s.setUrl("https://" + name + ".com/rss");
        s.setEnabled(true);
        return s;
    }

    @Test
    void updateNewsProcessesEachEnabledSource() {
        List<NewsSource> sources = List.of(source(1L, "BBC"), source(2L, "CNN"));
        when(sourceService.getEnabledSources()).thenReturn(sources);
        when(processingService.processSource(any())).thenReturn(5);

        service.updateNews();

        verify(processingService).processSource(sources.get(0));
        verify(processingService).processSource(sources.get(1));
    }

    @Test
    void updateNewsThrowsWhenNoSources() {
        when(sourceService.getEnabledSources()).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateNews())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No news sources");
    }

    @Test
    void updateNewsThrowsWhenAllSourcesFail() {
        List<NewsSource> sources = List.of(source(1L, "BBC"), source(2L, "CNN"));
        when(sourceService.getEnabledSources()).thenReturn(sources);
        when(processingService.processSource(any())).thenThrow(new BusinessException("feed down"));

        assertThatThrownBy(() -> service.updateNews())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("All");
    }

    @Test
    void updateNewsSucceedsWhenAtLeastOneSourceSavesZeroArticles() {
        List<NewsSource> sources = List.of(source(1L, "BBC"));
        when(sourceService.getEnabledSources()).thenReturn(sources);
        when(processingService.processSource(any())).thenReturn(0);

        service.updateNews();

        verify(processingService).processSource(sources.get(0));
    }
}
