package com.finance.news.service;

import com.finance.news.service.source.NewsSourceAdminService;
import com.finance.news.service.source.NewsSourceRefreshService;
import com.finance.news.service.source.NewsSourceService;


import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.news.client.RssClient;
import com.finance.news.mapper.NewsSourceMapper;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsArticleRepository;
import com.finance.news.repository.NewsSourceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsSourceAdminServiceTest {

    private NewsSourceRepository repository;
    private NewsArticleRepository articleRepository;
    private NewsSourceMapper mapper;
    private NewsSourceService sourceService;
    private NewsSourceRefreshService refreshService;
    private RssClient rssClient;
    private NewsSourceAdminService service;
    private MockedStatic<TransactionSynchronizationManager> tsmMock;

    @BeforeEach
    void setUp() {
        repository = mock(NewsSourceRepository.class);
        articleRepository = mock(NewsArticleRepository.class);
        mapper = mock(NewsSourceMapper.class);
        sourceService = mock(NewsSourceService.class);
        refreshService = mock(NewsSourceRefreshService.class);
        rssClient = mock(RssClient.class);
        service = new NewsSourceAdminService(repository, articleRepository, mapper, sourceService, refreshService, rssClient);
        tsmMock = mockStatic(TransactionSynchronizationManager.class);
    }

    @AfterEach
    void tearDown() {
        tsmMock.close();
    }

    private UpsertNewsSourceRequest request(String name) {
        UpsertNewsSourceRequest req = new UpsertNewsSourceRequest();
        req.setName(name);
        req.setUrl("https://" + name + ".com/rss");
        req.setSourceType("RSS");
        req.setDefaultCategory("CRYPTO");
        req.setEnabled(true);
        req.setSortOrder(0);
        return req;
    }

    private NewsSource entity(Long id, String name) {
        NewsSource s = new NewsSource();
        s.setId(id);
        s.setName(name);
        s.setUrl("https://" + name + ".com/rss");
        s.setEnabled(true);
        return s;
    }

    private NewsSourceResponse response(Long id, String name) {
        return new NewsSourceResponse(id, name, "https://" + name + ".com/rss", "RSS", "CRYPTO", true, 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createSavesAndReturnsResponse() {
        UpsertNewsSourceRequest req = request("BBC");
        NewsSource saved = entity(1L, "BBC");
        when(repository.findByNameIgnoreCase("BBC")).thenReturn(Optional.empty());
        when(mapper.toEntity(req)).thenReturn(saved);
        when(repository.save(saved)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response(1L, "BBC"));

        NewsSourceResponse result = service.create(req);

        verify(repository).save(saved);
        assertThat(result.name()).isEqualTo("BBC");
    }

    @Test
    void createThrowsOnDuplicateName() {
        UpsertNewsSourceRequest req = request("BBC");
        when(repository.findByNameIgnoreCase("BBC")).thenReturn(Optional.of(entity(1L, "BBC")));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.create(req));

        assertThat(thrown).isInstanceOf(BadRequestException.class).hasMessage("error.news.sourceNameExists");
        assertThat(((BadRequestException) thrown).getMessageArgs()).containsExactly("BBC");
    }

    @Test
    void updateSavesAndReturnsResponse() {
        NewsSource existing = entity(1L, "BBC");
        UpsertNewsSourceRequest req = request("BBC");
        when(sourceService.findOrThrow(1L)).thenReturn(existing);
        when(repository.findByNameIgnoreCase("BBC")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response(1L, "BBC"));

        service.update(1L, req);

        verify(mapper).updateEntity(req, existing);
        verify(repository).save(existing);
    }

    @Test
    void updateThrowsWhenRenamingToExistingName() {
        NewsSource target = entity(1L, "BBC");
        NewsSource other = entity(2L, "CNN");
        UpsertNewsSourceRequest req = request("CNN");
        when(sourceService.findOrThrow(1L)).thenReturn(target);
        when(repository.findByNameIgnoreCase("CNN")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void setEnabledTogglesFlag() {
        NewsSource existing = entity(1L, "BBC");
        existing.setEnabled(true);
        when(sourceService.findOrThrow(1L)).thenReturn(existing);

        service.setEnabled(1L, false);

        assertThat(existing.isEnabled()).isFalse();
        verify(repository).save(existing);
    }

    @Test
    void deletePurgesArticlesBeforeRemovingSource() {
        NewsSource existing = entity(1L, "BBC");
        when(sourceService.findOrThrow(1L)).thenReturn(existing);
        when(articleRepository.deleteBySourceId(1L)).thenReturn(7);

        service.delete(1L);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(articleRepository, repository);
        inOrder.verify(articleRepository).deleteBySourceId(1L);
        inOrder.verify(repository).delete(existing);
    }

    @Test
    void deleteAllowsZeroArticles() {
        NewsSource existing = entity(2L, "Reuters");
        when(sourceService.findOrThrow(2L)).thenReturn(existing);
        when(articleRepository.deleteBySourceId(2L)).thenReturn(0);

        service.delete(2L);

        verify(articleRepository).deleteBySourceId(2L);
        verify(repository).delete(existing);
    }

    @Test
    void updateThrowsWhenSourceMissing() {
        UpsertNewsSourceRequest req = request("BBC");
        when(sourceService.findOrThrow(99L)).thenThrow(new ResourceNotFoundException("News source not found: 99"));

        assertThatThrownBy(() -> service.update(99L, req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_throwBusinessException_when_rssFeedUnreachableOnCreate() {
        UpsertNewsSourceRequest req = request("Broken");
        when(repository.findByNameIgnoreCase("Broken")).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new ExternalApiException("RSS", "404 not found"))
                .when(rssClient).fetchFeed(req.getUrl());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.news.sourceUrlInvalid");
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_skipUrlValidation_when_updateKeepsExistingUrl() {
        NewsSource existing = entity(1L, "BBC");
        UpsertNewsSourceRequest req = request("BBC");
        when(sourceService.findOrThrow(1L)).thenReturn(existing);
        when(repository.findByNameIgnoreCase("BBC")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response(1L, "BBC"));

        service.update(1L, req);

        verify(rssClient, org.mockito.Mockito.never()).fetchFeed(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_validateUrl_when_updateChangesUrl() {
        NewsSource existing = entity(1L, "BBC");
        existing.setUrl("https://old.example.com/rss");
        UpsertNewsSourceRequest req = request("BBC");
        when(sourceService.findOrThrow(1L)).thenReturn(existing);
        when(repository.findByNameIgnoreCase("BBC")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(response(1L, "BBC"));

        service.update(1L, req);

        verify(rssClient).fetchFeed(req.getUrl());
    }
}
