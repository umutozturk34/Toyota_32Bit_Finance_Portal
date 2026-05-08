package com.finance.news.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.time.LocalDateTime;

public record NewsSourceResponse(
        Long id,
        String name,
        String url,
        String sourceType,
        String defaultCategory,
        boolean enabled,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
