package com.finance.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record UserLayoutResponse(
        String userSub,
        JsonNode overview,
        Instant updatedAt
) {
}
