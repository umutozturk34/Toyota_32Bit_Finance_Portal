package com.finance.news.dto.internal;

import java.time.LocalDateTime;

/** Raw article extracted from a feed entry, before classification or source attribution. */
public record RssArticleData(
        String title,
        String link,
        String description,
        String content,
        String imageUrl,
        String guid,
        LocalDateTime publishedAt
) {}
