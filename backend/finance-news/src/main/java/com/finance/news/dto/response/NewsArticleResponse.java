package com.finance.news.dto.response;

import java.time.LocalDateTime;

/** Compact article summary for list/feed views (no full body). */
public record NewsArticleResponse(
        Long id,
        String title,
        String description,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl
) {}
