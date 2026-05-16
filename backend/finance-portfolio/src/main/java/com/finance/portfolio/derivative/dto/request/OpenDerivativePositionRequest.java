package com.finance.portfolio.derivative.dto.request;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenDerivativePositionRequest(
        @NotBlank String contractSymbol,
        @NotNull DerivativeDirection direction,
        @NotNull @PastOrPresent LocalDate entryDate,
        BigDecimal entryPrice,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantityLot,
        @PastOrPresent LocalDate closeDate,
        BigDecimal closePrice
) { }
