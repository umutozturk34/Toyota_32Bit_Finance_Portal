package com.finance.app.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Identifies one instrument to analyse: its analytics type plus the source-specific asset/macro code. */
public record AnalyticsInstrument(
        @NotNull AnalyticsInstrumentType type,
        @NotBlank String code) {
}
