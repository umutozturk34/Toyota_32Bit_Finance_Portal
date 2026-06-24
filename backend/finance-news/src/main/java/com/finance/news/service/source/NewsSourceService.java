package com.finance.news.service.source;

import com.finance.news.dto.response.NewsSourceResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.news.mapper.NewsSourceMapper;
import com.finance.news.model.NewsSource;
import com.finance.news.repository.NewsSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-side access to news sources (enabled list for ingest, full list and single lookups for admin). */
@Log4j2
@Service
@RequiredArgsConstructor
public class NewsSourceService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsSourceMapper newsSourceMapper;

    /** Enabled sources in sort order; entity-returning for the ingest path (not API responses). */
    @Transactional(readOnly = true)
    public List<NewsSource> getEnabledSources() {
        return newsSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    /** All sources in sort order, or only enabled ones when {@code includeDisabled} is false. */
    @Transactional(readOnly = true)
    public List<NewsSourceResponse> getAllSources(boolean includeDisabled) {
        List<NewsSource> sources = includeDisabled
                ? newsSourceRepository.findAllByOrderBySortOrderAsc()
                : newsSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
        return newsSourceMapper.toResponses(sources);
    }

    /** Single source as an API response; {@link #findOrThrow} is the entity-returning variant for write paths. */
    @Transactional(readOnly = true)
    public NewsSourceResponse getById(Long id) {
        return newsSourceMapper.toResponse(findOrThrow(id));
    }

    /** Loads a source by id or throws {@link ResourceNotFoundException}; entity-returning for write-path callers. */
    public NewsSource findOrThrow(Long id) {
        return newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.news.sourceNotFound", id));
    }
}
