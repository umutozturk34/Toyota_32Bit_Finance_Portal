package com.finance.news.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Compact article summary for list/feed views (no full body). {@code assets} are the market codes it mentions. */
public record NewsArticleResponse(
        Long id,
        String title,
        String description,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl,
        List<NewsAssetResponse> assets
) {}
