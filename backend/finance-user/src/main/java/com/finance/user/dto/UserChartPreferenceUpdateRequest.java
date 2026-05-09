package com.finance.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UserChartPreferenceUpdateRequest(@NotNull Map<String, Object> config) {}
