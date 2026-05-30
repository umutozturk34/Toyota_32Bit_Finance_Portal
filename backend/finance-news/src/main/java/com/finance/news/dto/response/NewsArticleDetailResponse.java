package com.finance.news.dto.response;

import java.time.LocalDateTime;

/** Full single-article view including body content and original link. */
public record NewsArticleDetailResponse(
        Long id,
        String title,
        String link,
        String description,
        String content,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl
) {}
