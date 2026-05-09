package com.finance.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserChartDrawingResponse(List<Map<String, Object>> drawings, Instant updatedAt) {}
