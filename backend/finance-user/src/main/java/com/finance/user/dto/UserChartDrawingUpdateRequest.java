package com.finance.user.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UserChartDrawingUpdateRequest(@NotNull JsonNode drawings) {}
