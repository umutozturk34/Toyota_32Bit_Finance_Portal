package com.finance.news.dto.response;

import java.util.List;

/**
 * The most-mentioned assets (capped at the requested limit) together with {@code totalMentions} — the grand
 * total of asset mentions across ALL news. The total is the correct denominator for each asset's share %; using
 * the sum of only the capped list inflates every share once more than {@code limit} assets are mentioned.
 */
public record NewsAssetCountsResponse(List<NewsAssetCountResponse> assets, long totalMentions) {}
