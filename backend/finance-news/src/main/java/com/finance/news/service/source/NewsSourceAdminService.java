package com.finance.news.service.source;

import com.finance.news.service.source.NewsSourceAdminService;
import com.finance.news.service.source.NewsSourceRefreshService;
import com.finance.news.service.source.NewsSourceService;

import com.finance.news.dto.request.UpsertNewsSourceRequest;
import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.common.exception.BadRequestException;
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

@Log4j2
@Service
@RequiredArgsConstructor
public class NewsSourceAdminService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsSourceMapper newsSourceMapper;
    private final NewsSourceService newsSourceService;
    private final NewsSourceRefreshService newsSourceRefreshService;

    @Transactional
    public NewsSourceResponse create(UpsertNewsSourceRequest request) {
        validateNameUniqueness(request.getName(), null);
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

    @Transactional
    public NewsSourceResponse update(Long id, UpsertNewsSourceRequest request) {
        NewsSource entity = newsSourceService.findOrThrow(id);
        validateNameUniqueness(request.getName(), id);
        newsSourceMapper.updateEntity(request, entity);
        return newsSourceMapper.toResponse(newsSourceRepository.save(entity));
    }

    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        NewsSource entity = newsSourceService.findOrThrow(id);
        entity.setEnabled(enabled);
        newsSourceRepository.save(entity);
    }

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
}
