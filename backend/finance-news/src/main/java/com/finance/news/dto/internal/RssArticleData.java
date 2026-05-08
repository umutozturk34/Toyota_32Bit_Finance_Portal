package com.finance.news.dto.internal;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.time.LocalDateTime;

public record RssArticleData(
        String title,
        String link,
        String description,
        String content,
        String imageUrl,
        String guid,
        LocalDateTime publishedAt
) {}
