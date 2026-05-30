package com.finance.news.dto.response;

import java.time.LocalDateTime;

/** API view of a configured news source for the admin source-management UI. */
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
