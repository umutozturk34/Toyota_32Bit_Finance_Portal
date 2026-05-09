package com.finance.user.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record UserChartPreferenceResponse(JsonNode config, Instant updatedAt) {}
