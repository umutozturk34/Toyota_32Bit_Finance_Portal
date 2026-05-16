package com.finance.portfolio.derivative.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CloseDerivativePositionRequest(
        @NotNull @PastOrPresent LocalDate closeDate,
        BigDecimal closePrice
) { }
