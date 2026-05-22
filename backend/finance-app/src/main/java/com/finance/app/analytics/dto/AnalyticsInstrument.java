package com.finance.app.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalyticsInstrument(
        @NotNull AnalyticsInstrumentType type,
        @NotBlank String code) {
}
