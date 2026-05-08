package com.finance.news.service;

import com.finance.news.service.source.NewsSourceService;


import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.news.mapper.NewsSourceMapper;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSourceServiceTest {

    private NewsSourceRepository repository;
    private NewsSourceMapper mapper;
    private NewsSourceService service;

    @BeforeEach
    void setUp() {
        repository = mock(NewsSourceRepository.class);
        mapper = mock(NewsSourceMapper.class);
        service = new NewsSourceService(repository, mapper);
    }

    private NewsSource source(Long id, String name, boolean enabled) {
        NewsSource s = new NewsSource();
        s.setId(id);
        s.setName(name);
        s.setUrl("https://" + name + ".com/rss");
        s.setEnabled(enabled);
        s.setSortOrder(0);
        return s;
    }

    private NewsSourceResponse response(Long id, String name, boolean enabled) {
        return new NewsSourceResponse(id, name, "https://" + name + ".com/rss", "RSS", "CRYPTO", enabled, 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void getEnabledSourcesDelegatesToRepository() {
        NewsSource s = source(1L, "BBC", true);
        when(repository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(s));

        List<NewsSource> result = service.getEnabledSources();

        assertThat(result).containsExactly(s);
    }

    @Test
    void getAllSourcesIncludingDisabledCallsAllRepo() {
        NewsSource s1 = source(1L, "BBC", true);
        NewsSource s2 = source(2L, "CNN", false);
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(s1, s2));
        when(mapper.toResponses(List.of(s1, s2))).thenReturn(List.of(response(1L, "BBC", true), response(2L, "CNN", false)));

        List<NewsSourceResponse> result = service.getAllSources(true);

        assertThat(result).hasSize(2);
        verify(repository).findAllByOrderBySortOrderAsc();
    }

    @Test
    void getAllSourcesExcludingDisabledFiltersAtRepo() {
        NewsSource s = source(1L, "BBC", true);
        when(repository.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(s));
        when(mapper.toResponses(List.of(s))).thenReturn(List.of(response(1L, "BBC", true)));

        List<NewsSourceResponse> result = service.getAllSources(false);

        assertThat(result).hasSize(1);
        verify(repository).findByEnabledTrueOrderBySortOrderAsc();
    }

    @Test
    void getByIdReturnsResponseForExistingSource() {
        NewsSource s = source(1L, "BBC", true);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        when(mapper.toResponse(s)).thenReturn(response(1L, "BBC", true));

        NewsSourceResponse result = service.getById(1L);

        assertThat(result.name()).isEqualTo("BBC");
    }

    @Test
    void getByIdThrowsForMissingSource() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findOrThrowReturnsExistingSource() {
        NewsSource s = source(5L, "Reuters", true);
        when(repository.findById(5L)).thenReturn(Optional.of(s));

        NewsSource result = service.findOrThrow(5L);

        assertThat(result).isEqualTo(s);
    }

    @Test
    void findOrThrowThrowsForMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrThrow(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
