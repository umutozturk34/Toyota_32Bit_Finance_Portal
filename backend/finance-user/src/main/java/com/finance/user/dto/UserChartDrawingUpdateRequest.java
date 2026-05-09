package com.finance.user.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record UserChartDrawingUpdateRequest(@NotNull JsonNode drawings) {}
