package com.finance.portfolio.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionSellRequest(
        @NotNull @Positive BigDecimal quantity,
        @NotNull @Positive BigDecimal exitPrice,
        @NotNull LocalDateTime exitDate,
        String priceCurrency
) {
    public PositionSellRequest(BigDecimal quantity, BigDecimal exitPrice, LocalDateTime exitDate) {
        this(quantity, exitPrice, exitDate, null);
    }
}
