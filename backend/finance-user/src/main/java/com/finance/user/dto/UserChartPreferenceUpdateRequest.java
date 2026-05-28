package com.finance.user.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** Replaces the stored chart configuration with the supplied frontend-defined JSON payload. */
public record UserChartPreferenceUpdateRequest(@NotNull JsonNode config) {}
