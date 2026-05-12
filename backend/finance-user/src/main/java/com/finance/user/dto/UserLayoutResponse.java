package com.finance.user.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record UserLayoutResponse(
        String userSub,
        JsonNode overview,
        Instant updatedAt
) {
}
