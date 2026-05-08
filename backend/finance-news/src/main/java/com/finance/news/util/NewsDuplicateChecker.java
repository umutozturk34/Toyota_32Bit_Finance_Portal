package com.finance.news.util;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.news.dto.internal.RssArticleData;
import com.finance.news.repository.NewsArticleRepository;

public final class NewsDuplicateChecker {

    private NewsDuplicateChecker() {
    }

    public static boolean isDuplicate(RssArticleData data, NewsArticleRepository repository) {
        if (data.guid() != null && !data.guid().isBlank()) {
            if (repository.existsByGuid(data.guid())) {
                return true;
            }
        }
        return repository.existsByLink(data.link());
    }
}
