package com.finance.news.service;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

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
