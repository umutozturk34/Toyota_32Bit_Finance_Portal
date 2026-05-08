package com.finance.news.service.source;

import com.finance.news.service.source.NewsSourceProcessingService;
import com.finance.news.service.source.NewsSourceRefreshService;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.news.model.NewsSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class NewsSourceRefreshService {

    private final NewsSourceProcessingService newsSourceProcessingService;

    @Async("taskExecutor")
    public void processSourceAsync(NewsSource source) {
        try {
            int saved = newsSourceProcessingService.processSource(source);
            log.info("Async-processed new source '{}': {} articles saved", source.getName(), saved);
        } catch (Exception e) {
            log.warn("Async-process failed for new source '{}': {}", source.getName(), e.getMessage());
        }
    }
}
