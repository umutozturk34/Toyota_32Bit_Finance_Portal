package com.finance.news.util;

import com.finance.news.dto.internal.RssArticleData;
import com.finance.news.repository.NewsArticleRepository;

/** Stateless dedup check that prevents re-ingesting an article already stored under the same guid or link. */
public final class NewsDuplicateChecker {

    private NewsDuplicateChecker() {
    }

    /** True if an article with the same non-blank guid (preferred) or link already exists. */
    public static boolean isDuplicate(RssArticleData data, NewsArticleRepository repository) {
        if (data.guid() != null && !data.guid().isBlank()) {
            if (repository.existsByGuid(data.guid())) {
                return true;
            }
        }
        return repository.existsByLink(data.link());
    }
}
