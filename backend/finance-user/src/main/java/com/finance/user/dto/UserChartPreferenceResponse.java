package com.finance.user.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** Saved chart configuration payload (frontend-defined JSON) plus its last-modified timestamp. */
public record UserChartPreferenceResponse(JsonNode config, Instant updatedAt) {}
