package com.finance.backend.dto.response;

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
