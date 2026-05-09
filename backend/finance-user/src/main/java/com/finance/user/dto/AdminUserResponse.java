package com.finance.user.dto;

import java.time.Instant;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Instant createdAt
) {
}
