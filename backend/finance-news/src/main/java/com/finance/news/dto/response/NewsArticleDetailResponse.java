package com.finance.news.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Full single-article view including body content and original link. {@code assets} are the codes it mentions. */
public record NewsArticleDetailResponse(
        Long id,
        String title,
        String link,
        String description,
        String content,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl,
        List<NewsAssetResponse> assets
) {}
