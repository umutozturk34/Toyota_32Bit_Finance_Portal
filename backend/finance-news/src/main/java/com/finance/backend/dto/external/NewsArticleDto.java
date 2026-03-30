package com.finance.backend.dto.external;

import com.finance.backend.model.NewsCategory;

import java.time.LocalDateTime;

public record NewsArticleDto(
        String title,
        String link,
        String description,
        String content,
        String sourceName,
        String sourceUrl,
        NewsCategory category,
        LocalDateTime publishedAt,
        String imageUrl,
        String guid
) {}
