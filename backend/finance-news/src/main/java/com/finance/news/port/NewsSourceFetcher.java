package com.finance.news.port;

import com.finance.news.dto.internal.RssArticleData;

import java.util.List;

/** Abstraction over a remote feed reader; fetches and parses raw articles from a source URL. */
public interface NewsSourceFetcher {

    /** Fetches and parses the feed at the URL into raw article data; throws on network/parse failure. */
    List<RssArticleData> fetchFeed(String feedUrl);
}
