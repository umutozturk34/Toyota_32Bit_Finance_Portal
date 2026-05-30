package com.finance.user.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** Saved chart drawings payload (frontend-defined JSON) plus its last-modified timestamp. */
public record UserChartDrawingResponse(JsonNode drawings, Instant updatedAt) {}
