package com.finance.backend.dto.internal;

import java.time.LocalDateTime;

public record RssArticleData(
        String title,
        String link,
        String description,
        String imageUrl,
        LocalDateTime publishedAt
) {}
