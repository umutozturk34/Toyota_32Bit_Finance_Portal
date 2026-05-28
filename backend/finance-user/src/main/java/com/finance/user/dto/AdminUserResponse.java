package com.finance.user.dto;

import java.time.Instant;

/** Admin-facing view of a Keycloak user account, including the enabled flag for status management. */
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
