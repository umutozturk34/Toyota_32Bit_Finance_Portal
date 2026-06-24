package com.finance.news.service.source;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ExternalApiException;
import com.finance.news.client.RssClient;
import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.news.mapper.NewsSourceMapper;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsArticleRepository;
import com.finance.news.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Admin write operations for news sources (create/update/enable/delete). Enforces unique names and a
 * reachable URL before persisting; a newly created source is ingested asynchronously after commit, and
 * deleting a source first purges its articles.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NewsSourceAdminService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsSourceMapper newsSourceMapper;
    private final NewsSourceService newsSourceService;
    private final NewsSourceRefreshService newsSourceRefreshService;
    private final RssClient rssClient;

    /** Validates uniqueness and reachability, persists the source, and schedules a first ingest after commit. */
    @Transactional
    public NewsSourceResponse create(UpsertNewsSourceRequest request) {
        validateNameUniqueness(request.getName(), null);
        validateUrlReachable(request.getUrl());
        NewsSource entity = newsSourceMapper.toEntity(request);
        NewsSource saved = newsSourceRepository.save(entity);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                newsSourceRefreshService.processSourceAsync(saved);
            }
        });

        return newsSourceMapper.toResponse(saved);
    }

    /** Updates a source, re-checking name uniqueness and re-validating the URL only when it changed. */
    @Transactional
    public NewsSourceResponse update(Long id, UpsertNewsSourceRequest request) {
        NewsSource entity = newsSourceService.findOrThrow(id);
        validateNameUniqueness(request.getName(), id);
        if (!request.getUrl().equals(entity.getUrl())) {
            validateUrlReachable(request.getUrl());
        }
        newsSourceMapper.updateEntity(request, entity);
        return newsSourceMapper.toResponse(newsSourceRepository.save(entity));
    }

    /** Toggles a source's enabled flag; disabled sources are skipped by the ingest refresh. */
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        NewsSource entity = newsSourceService.findOrThrow(id);
        entity.setEnabled(enabled);
        newsSourceRepository.save(entity);
    }

    /** Deletes a source after purging all of its articles. */
    @Transactional
    public void delete(Long id) {
        NewsSource entity = newsSourceService.findOrThrow(id);
        int purged = newsArticleRepository.deleteBySourceId(entity.getId());
        log.info("Purged {} articles before deleting news source id={} name={}", purged, entity.getId(), entity.getName());
        newsSourceRepository.delete(entity);
    }

    private void validateNameUniqueness(String name, Long excludeId) {
        newsSourceRepository.findByNameIgnoreCase(name.trim())
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new BadRequestException("error.news.sourceNameExists", name);
                });
    }

    /** Confirms the feed can be fetched; rejects the request as a business error when it cannot. */
    private void validateUrlReachable(String url) {
        try {
            rssClient.fetchFeed(url);
        } catch (ExternalApiException ex) {
            log.warn("News source URL not reachable url={}: {}", url, ex.getMessage());
            throw new BusinessException("error.news.sourceUrlInvalid", url);
        }
    }
}
