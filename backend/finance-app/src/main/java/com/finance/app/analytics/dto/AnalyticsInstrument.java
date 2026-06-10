package com.finance.app.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Identifies one instrument to analyse: its analytics type plus the source-specific asset/macro code. */
public record AnalyticsInstrument(
        @NotNull AnalyticsInstrumentType type,
        // '=' is allowed because commodity futures codes carry it (Brent BZ=F, wheat ZW=F, copper HG=F);
        // without it those instruments failed @Valid with a 400 and silently "didn't calculate" in scenarios.
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[A-Za-z0-9._=-]+$") String code) {
}
