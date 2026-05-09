package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionRequest(
        @NotBlank String assetType,
        @NotBlank String assetCode,
        @NotNull @Positive BigDecimal quantity,
        @NotNull LocalDateTime entryDate,
        @NotNull @Positive BigDecimal entryPrice
) {}
