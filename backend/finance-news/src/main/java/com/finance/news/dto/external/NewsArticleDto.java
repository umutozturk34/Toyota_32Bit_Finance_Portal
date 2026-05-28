package com.finance.news.dto.external;

import com.finance.news.model.NewsCategory;

import java.time.LocalDateTime;

/** Classified article ready for persistence: feed data enriched with source identity and a resolved category. */
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
