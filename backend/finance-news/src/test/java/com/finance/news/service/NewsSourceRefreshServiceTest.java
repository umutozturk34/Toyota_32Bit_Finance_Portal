package com.finance.news.service;

import com.finance.news.model.NewsSource;
import com.finance.news.service.source.NewsSourceProcessingService;
import com.finance.news.service.source.NewsSourceRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSourceRefreshServiceTest {

    private NewsSourceProcessingService processingService;
    private NewsSourceRefreshService service;

    @BeforeEach
    void setUp() {
        processingService = mock(NewsSourceProcessingService.class);
        service = new NewsSourceRefreshService(processingService);
    }

    private NewsSource source() {
        NewsSource s = new NewsSource();
        s.setId(1L);
        s.setName("BBC");
        s.setUrl("https://bbc.co.uk/rss");
        return s;
    }

    @Test
    void should_delegateToProcessing_when_sourceProcessedAsync() {
        // Arrange
        NewsSource source = source();
        when(processingService.processSource(source)).thenReturn(4);

        // Act
        service.processSourceAsync(source);

        // Assert
        verify(processingService).processSource(source);
    }

    @Test
    void should_swallowException_when_processingFails() {
        // Arrange
        NewsSource source = source();
        when(processingService.processSource(any()))
                .thenThrow(new RuntimeException("feed unreachable"));

        // Act + Assert
        assertThatCode(() -> service.processSourceAsync(source)).doesNotThrowAnyException();
        verify(processingService).processSource(source);
    }
}
