package com.finance.news.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.finance.news.model.NewsCategory;

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
