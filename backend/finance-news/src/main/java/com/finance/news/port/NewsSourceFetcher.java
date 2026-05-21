package com.finance.news.port;

import com.finance.news.dto.internal.RssArticleData;

import java.util.List;

public interface NewsSourceFetcher {

    List<RssArticleData> fetchFeed(String feedUrl);
}
