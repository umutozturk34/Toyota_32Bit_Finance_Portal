package com.finance.news.port;

import java.util.List;

/**
 * Resolves the market assets a news article mentions. finance-news is a leaf module with no access to the asset
 * catalog (it depends only on finance-common), so the implementation lives in the monolith aggregator (finance-app),
 * which can see both the news ingest path and the stock repository. When no implementation is on the classpath
 * (finance-news running in isolation / tests) the ingest simply skips enrichment.
 */
public interface AssetMentionResolver {

    /** The assets named in {@code title}/{@code description}, or an empty list when none resolve. */
    List<ResolvedAsset> resolve(String title, String description);

    /** A resolved asset reference: the full market code (e.g. {@code "AHGAZ.IS"}) and its type ({@code "STOCK"}). */
    record ResolvedAsset(String code, String type) {}
}
