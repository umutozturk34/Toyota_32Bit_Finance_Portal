package com.finance.user.dto;

import java.time.Instant;
import java.util.Map;

public record UserChartPreferenceResponse(Map<String, Object> config, Instant updatedAt) {}
