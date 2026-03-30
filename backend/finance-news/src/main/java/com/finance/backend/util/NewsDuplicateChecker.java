package com.finance.backend.util;

import com.finance.backend.dto.internal.RssArticleData;
import com.finance.backend.repository.NewsArticleRepository;

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
