package com.finance.news.service.source;

import com.finance.news.service.source.NewsSourceService;

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

@Log4j2
@Service
@RequiredArgsConstructor
public class NewsSourceService {

    private final NewsSourceRepository newsSourceRepository;
    private final NewsSourceMapper newsSourceMapper;

    @Transactional(readOnly = true)
    public List<NewsSource> getEnabledSources() {
        return newsSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<NewsSourceResponse> getAllSources(boolean includeDisabled) {
        List<NewsSource> sources = includeDisabled
                ? newsSourceRepository.findAllByOrderBySortOrderAsc()
                : newsSourceRepository.findByEnabledTrueOrderBySortOrderAsc();
        return newsSourceMapper.toResponses(sources);
    }

    @Transactional(readOnly = true)
    public NewsSourceResponse getById(Long id) {
        return newsSourceMapper.toResponse(findOrThrow(id));
    }

    public NewsSource findOrThrow(Long id) {
        return newsSourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News source not found: " + id));
    }
}
