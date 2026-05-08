package com.finance.news.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.time.LocalDateTime;

public record NewsArticleResponse(
        Long id,
        String title,
        String description,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        String imageUrl
) {}
