package com.finance.user.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** A user's dashboard overview layout (frontend-defined JSON) with its last-modified timestamp. */
public record UserLayoutResponse(
        String userSub,
        JsonNode overview,
        Instant updatedAt
) {
}
