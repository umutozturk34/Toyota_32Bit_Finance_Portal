package com.finance.user.dto;

import java.time.Instant;
import java.util.Map;

public record UserLayoutResponse(
        String userSub,
        Map<String, Object> overview,
        Instant updatedAt
) {
}
