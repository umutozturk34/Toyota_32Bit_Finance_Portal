package com.finance.news.dto.response;

import java.time.LocalDateTime;

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
