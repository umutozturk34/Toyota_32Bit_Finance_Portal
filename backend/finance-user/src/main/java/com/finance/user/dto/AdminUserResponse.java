package com.finance.user.dto;

import java.time.Instant;
import java.util.List;

/**
 * Admin-facing view of a Keycloak user account, including the enabled flag for status management and the
 * user's meaningful app roles (USER/ADMIN) — Keycloak's built-in default realm roles are filtered out.
 */
public record AdminUserResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Instant createdAt,
        List<String> roles
) {
}
