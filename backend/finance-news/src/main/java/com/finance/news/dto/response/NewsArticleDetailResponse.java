package com.finance.news.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

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
