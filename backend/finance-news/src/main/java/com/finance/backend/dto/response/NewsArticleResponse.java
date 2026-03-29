package com.finance.backend.dto.response;

import java.time.LocalDateTime;

public record NewsArticleResponse(
        Long id,
        String title,
        String link,
        String description,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl
) {}
