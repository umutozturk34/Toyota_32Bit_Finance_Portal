package com.finance.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record UserChartDrawingUpdateRequest(@NotNull List<Map<String, Object>> drawings) {}
